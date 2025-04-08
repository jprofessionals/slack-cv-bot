package no.jpro.slack.cv

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.cloud.Timestamp
import com.google.cloud.firestore.FirestoreOptions
import com.slack.api.Slack
import com.slack.api.model.Message
import com.slack.api.model.block.ActionsBlock
import com.slack.api.model.block.SectionBlock
import com.slack.api.model.block.composition.PlainTextObject
import com.slack.api.model.block.element.ButtonElement
import com.sun.net.httpserver.HttpServer
import io.github.oshai.kotlinlogging.KotlinLogging
import no.jpro.slack.SectionSelection
import no.jpro.slack.SlackEvent
import no.jpro.slack.SlackThread
import no.jpro.slack.SlashCommand
import no.jpro.slack.cv.flowcase.CVReader
import no.jpro.slack.cv.flowcase.FlowcaseService
import no.jpro.slack.cv.openai.OpenAIClient
import org.apache.avro.io.Decoder
import org.apache.avro.io.DecoderFactory
import org.apache.avro.specific.SpecificDatumReader
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.time.ZonedDateTime
import java.util.*
import java.util.stream.Collectors

val objectMapper: ObjectMapper = jacksonObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .registerModule(JavaTimeModule())

private val log = KotlinLogging.logger {}
private val openAIClient = OpenAIClient()
private val cvReader = CVReader()
private val slack = Slack.getInstance().methods(getEnvVariableOrThrow("SLACK_BOT_TOKEN"))
private val firestore = FirestoreOptions.newBuilder().setDatabaseId("slack-cv-bot").build().service

private val whichSectionQuestion = "Hvilken seksjon ønsker du jeg skal vurdere?"
private val whichSectionQuestionBlock = SectionBlock.builder()
    .text(PlainTextObject(whichSectionQuestion, false))
    .build()

private val promptFormatString = """
    Vurder cv mellom <CV> og </CV> og gi en kort vurdering
    <CV>
    %s
    </CV>
"""
val summaryPromtFormatString =   """
  <ROLLE>
  Du er ekspert på å vurdere sammendrag av nøkkelkvalifikasjoner for CV skrevet av IT-konsulent.
  </ROLLE>

  <INNSTRUKS>
  Du skal:
  - Bruke oppgitte retningslinjer og kriterier mellom <RETNINGSLINJER> og </RETNINGSLINJER> for å vurdere tekst mellom <SAMMENDRAG> og </SAMMENDRAG>. Fokuser på konstruktiv tilbakemelding 
  til bruker, du skal begrunne dine vurderinger med utgangspunkt i retningslinjer og gi konkrete forslag til forbedringer. 
  - Lage et oppdatert sammendrag med referanser til erfarenheter fra prosjekter konsulenten har jobbet på. Bruk informasjonen mellom <PROSJEKTBESKRIVELSER> og </PROSJEKTBESKRIVELSER>.
  </INNSTRUKS>
  
  <RETNINGSLINJER>
  Sammendraget skal oppsummere konsulentens kompetanse, erfaring og personlige egenskaper. Sammendraget skal gi innsikt 
  i hvem konsulenten er, dennes bakgrunn og hva konsulenten kan bidra med. Sammendraget er konsulentens 'elevator pitch'. 
  Hvis konsulenten har 5, 10 eller 15 års erfaring, skal sammendraget være mer enn et par setninger. Det kan deles likt mellom: 
  - Tekniske ferdigheter: Konsulenten skal beskrive sine viktigste tekniske ferdigheter og løfte frem erfaringer, viktige roller og ansvar 
  denne har hatt. 
  - Team/organisering/metodikk: Konsulentens erfaringer i tverrfaglige team, med organisering og metodikk skal beskrives. Konsulenten burde nevne hvordan 
  denne beriker team/menneskene rundt seg og beskrive sitt verdibidraget. Det bør vises til både selvstendighet og samspill med andre 
  (også andre fagdisipliner). 
  - Personlige egenskaper: Konsulenten skal si noe om hva denne er spesielt engasjert i/opptatt av/interessert i.
  </RETNINGSLINJER>
  
  <SAMMENDRAG>
  %s
  </SAMMENDRAG>
  <PROSJEKTBESKRIVELSER>
  %s
  </PROSJEKTBESKRIVELSER>
  """

private const val CUSTOM_EVENT_TYPE = "cv_lest"
private const val OPENAI_THREAD = "openai_thread_id"

private val reader: SpecificDatumReader<SlackEvent> = SpecificDatumReader(SlackEvent.getClassSchema())

fun main() {
    log.info { "Starting slack-cv-bot-handler" }
    val httpServer: HttpServer = HttpServer.create(InetSocketAddress(8080), 16)
    httpServer.createContext("/") { exchange ->
        log.info { "Handling request, method=${exchange.requestMethod}, path=${exchange.httpContext.path}" }
        try {
            BufferedReader(InputStreamReader(exchange.requestBody)).use { bufferedReader ->
                val bodyAsString = bufferedReader.lines().collect(Collectors.joining("\n"))
                log.info { "Body: '$bodyAsString'" }
                val message: PubSubBody = objectMapper.readValue(bodyAsString, PubSubBody::class.java)
                val encodedData = message.message.data
                val data = Base64.getDecoder().decode(encodedData)
                ByteArrayInputStream(data).use { inputStream ->
                    when (val event = decodeAvro(inputStream).event) {
                        is SlashCommand -> handleSlashCommand(event)
                        is SectionSelection -> handleSectionSelection(event)
                        else -> log.info { "Ignoring unknown event type: ${event::class.java}" }
                    }
                }
            }
        } catch (e: java.lang.Exception) {
            log.warn(e) { "Error processing request" }
        }
        exchange.sendResponseHeaders(200, 0)
        exchange.responseBody.close()
    }
    httpServer.start()
}

private fun decodeAvro(inputStream: ByteArrayInputStream): SlackEvent {
    val decoder: Decoder = DecoderFactory.get().directBinaryDecoder(inputStream, null)
    return reader.read(null, decoder)
}

fun handleSectionSelection(sectionSelection: SectionSelection) {
    slack.chatPostMessage {
        it
            .channel(sectionSelection.slackThread.channelId)
            .threadTs(sectionSelection.slackThread.threadTs)
            .text("Knapper er ikke implementert enda")
    }
}

fun handleSlashCommand(slackSlashCommand: SlashCommand) {
    slack.chatPostMessage {
        it
            .channel(slackSlashCommand.slackThread.channelId)
            .threadTs(slackSlashCommand.slackThread.threadTs)
            .text("Henter CV fra Flowcase")
    }
    val cv = cvReader.readCV(slackSlashCommand.userEmail)//TODO: what if cv not found

    writeToDatastore(slackSlashCommand.slackThread, slackSlashCommand.userEmail)

    val message = slack.chatPostMessage {
        it
            .channel(slackSlashCommand.slackThread.channelId)
            .threadTs(slackSlashCommand.slackThread.threadTs)
            .text(whichSectionQuestion)
            .blocks(listOf(whichSectionQuestionBlock, createActionBlock(cv)))
    }
    log.debug { message }

    val summary = cv.key_qualifications.find { !it.disabled }?.long_description?.no?:""//TODO: what if no summary
    val projects = cv.project_experiences.map { "<PROSJEKTBESKRIVELSE><PROSJEKT>${it.customer.no} - ${it.description.no} (fra: ${it.month_from}.${it.year_from} til: ${it.month_to}.${it.year_to})</PROSJEKT><BESKRIVELSE>${it.long_description.no?:""}</BESKRIVELSE></PROSJEKTBESKRIVELSE>" }.joinToString()

    slack.chatPostMessage {
        it
            .channel(slackSlashCommand.slackThread.channelId)
            .threadTs(slackSlashCommand.slackThread.threadTs)
            .text("Sender sammendrag til OpenAI for vurdering")
    }

    openAIClient.startNewThread(
        message = String.format(summaryPromtFormatString, summary, projects),
        onAnswer = { answer, openAiThread ->
            log.info { "Received answer from OpenAI" }
            slack.chatPostMessage {
                it
                    .channel(slackSlashCommand.slackThread.channelId)
                    .threadTs(slackSlashCommand.slackThread.threadTs)
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

private fun createActionBlock(cv: FlowcaseService.FlowcaseCv): ActionsBlock? {
    val keyQualificationElements = cv.key_qualifications
        .filter { !it.disabled }
        .map { keyQualification ->
            ButtonElement.builder()
                .text(PlainTextObject("Sammendrag", false))
                .value("key_qualification-${keyQualification._id}")
                .build()

        }
    val projectExperienceElements = cv.project_experiences
        .filter { !it.disabled }
        .sortedWith(compareBy({ it.year_from }, { it.month_from }))
        .reversed()
        .map { projectExperience ->
            ButtonElement.builder()
                .text(PlainTextObject(projectExperience.description.no, false))
                .value("project_experience-${projectExperience._id}")
                .build()
        }
    return ActionsBlock.builder()
        .blockId("sectionSelection")
        .elements(keyQualificationElements.plus(projectExperienceElements))
        .build()
}

data class FirestoreThread(
    val userEmail: String,
    val expiresAt: Timestamp = Timestamp.of(Date.from(ZonedDateTime.now().plusDays(7).toInstant()))
)

private fun writeToDatastore(slackThread: SlackThread, userEmail: String) {
    val id = "${slackThread.channelId}#${slackThread.threadTs}"
    log.debug { "Writing to firestore: id=$id" }
    val result = firestore.collection("threads").document(id).set(FirestoreThread(userEmail))
    log.info { "Wrote to firestore: ${result.get()}" }
}

fun getEnvVariableOrThrow(variableName: String) = System.getenv().get(variableName)
    ?: throw Exception("$variableName not defined in environment variables")
