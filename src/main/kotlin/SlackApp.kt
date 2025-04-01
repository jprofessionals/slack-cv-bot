package no.jpro.slack.cv

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.slack.api.bolt.App
import com.slack.api.bolt.context.builtin.EventContext
import com.slack.api.model.Message
import com.slack.api.model.event.MessageEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import no.jpro.slack.cv.flowcase.CVReader
import no.jpro.slack.cv.openai.OpenAIClient

private const val CUSTOM_EVENT_TYPE = "cv_lest"

private const val OPENAI_THREAD = "openai_thread_id"
private val log = KotlinLogging.logger {}

private val objectMapper = jacksonObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)


class SlackApp(val app: App = App()) {
    private val openAIClient = OpenAIClient()
    private val cvReader = CVReader()

    init {
        // Rekkefølge betyr noe...
        slashCommandLesCV()
        pingMessage()
        replyInThread()
    }

    private fun replyInThread() {
        app.event(MessageEvent::class.java) { payload, ctx ->
            val event = payload.event
            app.executorService().submit {
                replyMessageInThread(event, ctx)
            }
            ctx.ack()
        }
    }

    private fun pingMessage() {
        app.message("ping") { payload, ctx ->
            val event = payload.event
            log.debug { "Answer pong" }
            ctx.say {
                it
                    .text("pong ${ctx.retryNum}")
                    .threadTs(event.threadTs ?: event.ts)
                    .channel(event.channel)
            }
            ctx.ack()
        }
    }


    /** Starter en ny tråd i slack  */
    private fun slashCommandLesCV() {
        app.command("/lescv") { req, ctx ->
            timing("/lescv") {
                val payload = req.payload
                log.debug { "Slash command  /lescv" }
                app.executorService().submit {
                    //                ctx.respond{it.text("Vent ett øyeblikk mens jeg laster ned CV og gjør ting klart.")}
                    ctx.respond { it.text("OK, leser deg klart og tydelig. Sjekker CV for ${payload.userName}") }
                    val email = getUserEmail(payload.userId)
                    ctx.respond { it.text("Laster ned CV for $email") }
                    if (email.isNullOrEmpty()) {
                        throw IllegalArgumentException()
                    }
                    val cv = timing("readCV") { cvReader.readCV(email) }
                    ctx.respond { it.text("OK, Fant CVen din. Gi meg litt tid til å lese gjennom.") }
                    val jsonCV = timing("writeCVAsJSON") { objectMapper.writeValueAsString(cv) }
                    timing("openAIStartNewThread") {
                        openAIClient.startNewThread(
                            message = "Vurder cv mellom <CV> og </CV> og gi ett kort vurdering <CV>\n $jsonCV \n</CV> ",
                            onAnswer = { answer, openAiThread ->
                                ctx.respond {
                                    it
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
                    }
                }
                ctx.ack()
            }
        }
    }


    private fun replyMessageInThread(
        event: MessageEvent,
        ctx: EventContext
    ) {
//        val usersInfo = getUserInfo(event)
//        ctx.logger.info("event result - ts {} threadts {} channel {}", event.ts, event.threadTs, event.channel)
//        ctx.say("<@${event.user}> / ${usersInfo?.user?.profile?.email} Jeg forstår ikke hva du mener")
        val replies = history(event)
//        ctx.logger.info("replies {}", replies?.map { "${it.user}  ${it.text}\n" })
        val openAiThread = replies?.map { it.metadata }
            ?.first { it.eventType == CUSTOM_EVENT_TYPE }
            ?.eventPayload?.get(OPENAI_THREAD).toString()
        log.debug { "replies to openai thread $openAiThread" }
        if (openAiThread.isBlank()) {
            ctx.say {
                it
                    .text("Finner ikke noe openai tråd. Så jeg vet ikke helt hva du holder på med")
                    .threadTs(event.threadTs ?: event.ts)
                    .channel(event.channel)
            }
            return
        }

        openAIClient.chatInThread(
            message = event.text,
            openAiThreadId = openAiThread,
            onAnswer = { answer, threadId ->
                ctx.say {
                    it
                        .text(answer)
                        .threadTs(event.threadTs ?: event.ts)
                        .channel(event.channel)

                }
            }
        )

//        ctx.say {
//            it
//                .text("jasså du sier ${event.text}")
//                .threadTs(event.threadTs ?: event.ts)
//                .channel(event.channel)
//        }
    }

    private fun history(event: MessageEvent): List<Message>? {

        val replies = app.client.conversationsReplies {
            it.ts(event.threadTs)
                .channel(event.channel)
                .includeAllMetadata(true)
        }
        return replies.messages;
    }

    private fun getUserEmail(userid: String): String? {
        val usersInfo = app.client.usersInfo { it.user(userid) }
        return usersInfo?.user?.profile?.email
    }
}

