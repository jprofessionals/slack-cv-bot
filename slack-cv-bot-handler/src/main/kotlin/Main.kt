package no.jpro.slack.cv

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.slack.api.Slack
import com.slack.api.model.Message
import com.sun.net.httpserver.HttpServer
import io.github.oshai.kotlinlogging.KotlinLogging
import no.jpro.slack.cv.flowcase.CVReader
import no.jpro.slack.cv.openai.OpenAIClient
import java.net.InetSocketAddress

val objectMapper: ObjectMapper = jacksonObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .registerModule(JavaTimeModule())

private val log = KotlinLogging.logger {}
private val openAIClient = OpenAIClient()
private val cvReader = CVReader()
private val slack = Slack.getInstance().methods()

private val promptFormatString = """
    Vurder cv mellom <CV> og </CV> og gi en kort vurdering
    <CV>
    %s
    </CV>
"""
private const val CUSTOM_EVENT_TYPE = "cv_lest"
private const val OPENAI_THREAD = "openai_thread_id"

fun main() {
    log.info { "Starting slack-cv-bot-handler" }

    startHttpServer()

    receive { slackSlashCommand ->
        try {
            slack.chatPostMessage {
                it.threadTs(slackSlashCommand.threadTs)
                    .text("Henter CV fra Flowcase")
            }
            val cv = cvReader.readCV(slackSlashCommand.userEmail)
            val jsonCV = objectMapper.writeValueAsString(cv)
            slack.chatPostMessage {
                it.threadTs(slackSlashCommand.threadTs)
                    .text("Sender CV til OpenAI for vurdering")
            }
            openAIClient.startNewThread(
                message = String.format(promptFormatString, jsonCV),
                onAnswer = { answer, openAiThread ->
                    slack.chatPostMessage {
                        it
                            .threadTs(slackSlashCommand.threadTs)
                            .text(answer)
                            .metadata(
                                Message.Metadata.builder()
                                    .eventType(CUSTOM_EVENT_TYPE)
                                    .eventPayload(mapOf<String?, String?>(OPENAI_THREAD to openAiThread))
                                    .build()
                            )
                    }
                }
            )
        } catch (e: Exception) {
            log.error(e) { "Noe gikk galt" }
        }
    }
}

fun startHttpServer() {
    val httpServer: HttpServer = HttpServer.create(InetSocketAddress(8080), 16)
    httpServer.createContext("/") { exchange ->
        log.info { "Handling request, method=${exchange.requestMethod}, path=${exchange.httpContext.path}" }
        exchange.sendResponseHeaders(200, 0)
        exchange.responseBody.close()
    }
    httpServer.start()
}

fun getEnvVariableOrThrow(variableName: String) = System.getenv().get(variableName)
    ?: throw Exception("$variableName not defined in environment variables")
