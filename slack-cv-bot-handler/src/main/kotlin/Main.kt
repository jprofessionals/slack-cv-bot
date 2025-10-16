package no.jpro.slack.cv

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.cloud.Timestamp
import com.google.cloud.firestore.FirestoreOptions
import com.google.cloud.firestore.SetOptions
import com.slack.api.Slack
import com.slack.api.methods.kotlin_extension.request.chat.blocks
import com.slack.api.model.block.DividerBlock
import com.slack.api.model.block.SectionBlock
import com.slack.api.model.block.composition.OptionObject
import com.slack.api.model.block.composition.PlainTextObject
import com.slack.api.model.block.element.StaticSelectElement
import com.sun.net.httpserver.HttpServer
import io.github.oshai.kotlinlogging.KotlinLogging
import no.jpro.slack.*
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

const val whichSectionQuestion = "Hvilken seksjon ønsker du jeg skal vurdere?"
private val whichSectionQuestionBlock = SectionBlock.builder()
    .text(PlainTextObject(whichSectionQuestion, false))
    .build()

private const val buttonLimit = 6

private const val summaryPromptFormatString = """
<ROLLE>
  Du er ekspert på å vurdere sammendrag av nøkkelkvalifikasjoner for CV skrevet av IT-konsulent. 
</ROLLE>

<INNSTRUKS>
  Du skal:
  - Bruke oppgitte retningslinjer og kriterier mellom <RETNINGSLINJER> og </RETNINGSLINJER> for å vurdere teksten mellom <SAMMENDRAG> og </SAMMENDRAG>. Fokuser på konstruktiv tilbakemelding 
  til bruker, du skal begrunne dine vurderinger med utgangspunkt i retningslinjer og gi konkrete forslag til forbedringer og påpeke svakheter og eventuelle ting som bør endres. Det kan også poengteres om sammendraget er godt. 
  - Lage et oppdatert sammendrag med referanser til erfarenheter fra prosjekter konsulenten har jobbet på. Bruk informasjonen mellom <PROSJEKTBESKRIVELSER> og </PROSJEKTBESKRIVELSER>.
</INNSTRUKS>
  
<RETNINGSLINJER>
  Sammendraget skal oppsummere konsulentens kompetanse, erfaring og personlige egenskaper. Sammendraget skal gi innsikt 
  i hvem konsulenten er, dennes bakgrunn og hva konsulenten kan bidra med. Sammendraget er konsulentens 'elevator pitch'. 
  Hvis konsulenten har 5, 10 eller 15 års erfaring, skal sammendraget være mer enn et par setninger. Det kan deles omtrentlig likt mellom, men må ikke nødvendigvis ha eksakt samme oppbygning 
  så lenge alle aspekter er dekket: 
  - Tekniske ferdigheter: Konsulenten skal beskrive sine viktigste tekniske ferdigheter og løfte frem erfaringer, viktige roller og ansvar 
  denne har hatt. 
  - Team/organisering/metodikk: Konsulentens erfaringer i tverrfaglige team, med organisering og metodikk skal beskrives. Konsulenten burde nevne hvordan 
  denne beriker team/menneskene rundt seg og beskrive sitt verdibidraget. Det bør vises til både selvstendighet og samspill med andre 
  (også andre fagdisipliner). 
  - Personlige egenskaper: Konsulenten skal si noe om hva denne er spesielt engasjert i/opptatt av/interessert i.
  
  Sørg for at følgende regler er overholdt:
  - Skriv alltid i tredjeperson og i fortid, 
  - Skriv hele setninger, unngå stikkord eller punktlister
  - Vurder bruk av forkortelser, ikke alle forstår terminologien - velkjente termer kan fint benyttes
  - Sørg for å skrive utdypende og i detalj med gode avsnitt
  - Unngå slurvefeil og sjekk stavefeil
  - Språket trenger ikke være veldig formelt, bruk gjerne fornavn på konsulenten
</RETNINGSLINJER>
  
<SAMMENDRAG>
  %s
</SAMMENDRAG>
<PROSJEKTBESKRIVELSER>
  %s
</PROSJEKTBESKRIVELSER>
"""


private const val projectPromptFormatString = """
<ROLLE>
Du er ekspert på å vurdere beskrivelse av en prosjekt i IT-konsulents CV.
</ROLLE>

<INNSTRUKS>
Du skal:
- Bruke oppgitte retningslinjer og kriterier mellom <RETNINGSLINJER> og </RETNINGSLINJER> for å vurdere tekst mellom <PROSJEKT> og </PROSJEKT>. Fokuser på konstruktiv tilbakemelding
til bruker, du skal begrunne dine vurderinger med utgangspunkt i retningslinjer og gi konkrete forslag til forbedringer og påpeke svakheter og eventuelle ting som bør endres.
- Er dette en god prosjektbesrivelse? Hvis noe mangler eller strider mot rettningslinjene si det eksplisitt. Det kan også poengteres om beskrivelsen er god.
</INNSTRUKS>

<RETNINGSLINJER>
Bekrivelsen skal inneholde følgende; Prosjekt (tittel), Kunde, Periode, Prosjektbeskrivelse, en eller flere roller (som består av Rolle (tittel) og Rollebeskrivelse) og en liste med Teknologi/Verktøy/Metode.
Teksten må gi leseren god innsikt i hva som ble laget og hvordan. Inkluder konkrete detaljer om utfordringer som ble løst, hvordan og gi innsikt i teknologi, språk og verktøy som ble benyttet i prosjektet. Løft frem viktige leveranser, utmerkelser, prestasjoner ol.

** En beskrivelsen kan deles opp på følgende måte: **
- Om kunden, sirka 10%% av innhold. Introduserer kort kunden for leseren og forklarer kort hva kunden drev med. Ting som kan nevnes er; forretningsområde, deres kunder, brukere ol. Vurder også hvor allment kjent kunde/prosjekt/forretning er, om kunden er svært godt kjent trenger den mindre introduksjon.
- Om prosjektet, sirka 20%% av innhold. Forklar kort hva prosjektet gikk ut på. Elementer som kan nevnes er; hvor stort var prosjekte (f.eks. omfang i tid, resurser, kostnad ol.), konkrete mål, leveranser, verdiskapning ol. i prosjektet, hvordan var prosjektet organisert.
- Om teamet, sirka 20%% av innhold. Beskriv kort teamet og dets sammensetning. Elemnter som kan nevnes er antall deltakere, ulike roller og ansvarsområder, metodikk og arbeidsmåte.
- Om konsulentens rolle og bidrag, 50%% av innholdet. Fremhev i tekst det viktigste og mest relevante innen teknologi, verktøy, metode ol. fra prosjektet. Konkretiser hva konsulenten gjorde og hvordan?

** Sørg også for at følgende regler er overholdt i Prosjektbeskrivelse og Rollebeskrivelse: **
- Skriv alltid i tredjeperson og i fortid.
- Skriv hele setninger, unngå stikkord eller punktlister.
- Vurder bruk av forkortelser, ikke alle forstår terminologien - velkjente termer kan fint benyttes.
- Sørg for å skrive utdypende og i detalj.
- Unngå slurv og sjekk stavefeil.
- Språket trenger ikke være veldig formelt, bruk gjerne fornavn på konsulenten.
</RETNINGSLINJER>
<PROSJEKT>
%s
</PROSJEKT>
"""

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
                        is ThreadMessage -> handleThreadMessage(event)
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

fun handleThreadMessage(threadMessage: ThreadMessage) {
    val firestoreThread = readFromFirestore(threadMessage.slackThread)
    if (firestoreThread == null) {
        slack.chatPostMessage {
            it
                .channel(threadMessage.slackThread.channelId)
                .threadTs(threadMessage.slackThread.threadTs)
                .text("Jeg har glemt denne samtalen. Kan du starte en ny en?")
        }
        return
    }
    if (firestoreThread.openAiThreadId == null) {
        slack.chatPostMessage {
            it
                .channel(threadMessage.slackThread.channelId)
                .threadTs(threadMessage.slackThread.threadTs)
                .text("Du har ikke spurt meg om en vurdering enda.")
        }
        return
    }
    openAIClient.chatInThread(
        threadMessage.text,
        firestoreThread.openAiThreadId,
        onAnswer = { answer, _ ->
            log.info { "Received answer from OpenAI" }
            respondInThread(threadMessage.slackThread, answer)
        }
    )
}

private fun decodeAvro(inputStream: ByteArrayInputStream): SlackEvent {
    val decoder: Decoder = DecoderFactory.get().directBinaryDecoder(inputStream, null)
    return reader.read(null, decoder)
}

fun handleSectionSelection(sectionSelection: SectionSelection) {
    val firestoreThread = readFromFirestore(sectionSelection.slackThread)
    if (firestoreThread == null) {
        slack.chatPostMessage {
            it
                .channel(sectionSelection.slackThread.channelId)
                .threadTs(sectionSelection.slackThread.threadTs)
                .text("Jeg har glemt denne samtalen. Kan du starte en ny en?")
        }
        return
    }
    val userEmail = firestoreThread.userEmail
    val cv = cvReader.readCV(userEmail) // TODO: what if cv not found
    val sectionDetails = getSectionDetails(sectionSelection, cv)
    if (sectionDetails == null) {
        slack.chatPostMessage {
            it
                .channel(sectionSelection.slackThread.channelId)
                .threadTs(sectionSelection.slackThread.threadTs)
                .text("Jeg fant ikke riktig seksjon i CVen din, dette er mest sannsynlig en bug.")
        }
        return
    }

    slack.chatPostMessage {
        val text = "Sender *${sectionDetails.title}* til OpenAI for vurdering"
        it
            .channel(sectionSelection.slackThread.channelId)
            .threadTs(sectionSelection.slackThread.threadTs)
            .text(text)
            .blocks {
                section { markdownText(text) }
                sectionDetails.description?.let { description -> section { markdownText(description.markdownQuoteBlock()) } }
            }
    }

    val openAiThreadId = openAIClient.startNewThread(
        message = sectionDetails.prompt,
        onAnswer = { answer, _ ->
            log.info { "Received answer from OpenAI" }
            respondInThread(sectionSelection.slackThread, answer)

            askForSectionSelection(sectionSelection.slackThread.channelId, sectionSelection.slackThread.threadTs, cv)
        }
    )
    updateFirestoreWithOpenAiThreadId(sectionSelection.slackThread, openAiThreadId)
}

private fun respondInThread(slackThread: SlackThread, answer: String) {
    slack.chatPostMessage {
        it
            .channel(slackThread.channelId)
            .threadTs(slackThread.threadTs)
            .text(answer.replace("**", "*"))//By replacing this markdown is usually accepted by slack
    }
}

private fun getSectionDetails(
    sectionSelection: SectionSelection,
    cv: FlowcaseService.FlowcaseCv
): SectionDetails? {
    return when (sectionSelection.sectionType) {
        SectionType.KEY_QUALIFICATION -> {
            val keyQualification = cv.key_qualifications.orEmpty().firstOrNull { it._id == sectionSelection.sectionId }
            val projects = cv.project_experiences.orEmpty().filter { !it.disabled }
                .joinToString("\n") { "<PROSJEKTBESKRIVELSE>${getProjectDescription(it)}</PROSJEKTBESKRIVELSE>" }
            val prompt = String.format(summaryPromptFormatString, keyQualification?.long_description?.no, projects)
            return SectionDetails("Sammendrag", prompt, keyQualification?.long_description?.no)
        }
        SectionType.PROJECT_EXPERIENCE -> {
            val projectExperience = cv.project_experiences.orEmpty().firstOrNull { it._id == sectionSelection.sectionId }
            val title = projectExperience?.description?.no
            return if (title != null) {
                val projectDescription = getProjectDescription(projectExperience)
                val prompt = String.format(projectPromptFormatString, projectDescription)
                SectionDetails(title, prompt, projectDescription)
            } else {
                null
            }
        }
        null -> {
            log.warn { "sectionType was null" }
            null
        }
    }
}

data class SectionDetails (
    val title: String,
    val prompt: String,
    val description: String?,
)

fun handleSlashCommand(slackSlashCommand: SlashCommand) {
    slack.chatPostMessage {
        it
            .channel(slackSlashCommand.slackThread.channelId)
            .threadTs(slackSlashCommand.slackThread.threadTs)
            .text("Henter CV fra Flowcase")
    }
    val cv = cvReader.readCV(slackSlashCommand.userEmail)//TODO: what if cv not found

    writeEmailToDatastore(slackSlashCommand.slackThread, slackSlashCommand.userEmail)

    askForSectionSelection(slackSlashCommand.slackThread.channelId, slackSlashCommand.slackThread.threadTs, cv)
}

private fun askForSectionSelection(
    channelId: String,
    threadTs: String,
    cv: FlowcaseService.FlowcaseCv
) {
    val message = slack.chatPostMessage {
        it
            .channel(channelId)
            .threadTs(threadTs)
            .text(whichSectionQuestion)
            .blocks(listOf(DividerBlock.builder().build(), questionSection(cv)))
    }
    log.debug { message }
}

private fun questionSection(cv: FlowcaseService.FlowcaseCv): SectionBlock {
    val keyQualificationOptionElements = cv.key_qualifications.orEmpty()
        .filter { !it.disabled }
        .map { keyQualification ->
            OptionObject.builder()
                .text(
                    PlainTextObject.builder()
                        .text("Sammendrag")   // alltid ikke-tom
                        .emoji(false)
                        .build()
                )
                .value("key_qualification-${keyQualification._id}")
                .build()
        }

    val projectExperienceOptionElements = cv.project_experiences.orEmpty()
        .filter { !it.disabled }
        .sortedWith(compareBy({ it.year_from }, { it.month_from }))
        .reversed()
        .mapNotNull { pe ->
            // Sikker etikett med fallback + lengdebegrensning (Slack: maks 75)
            val label = getOptionText_NO(pe).trim().ifEmpty { "Ikke-navngitt prosjekt - Ikke-navngitt kunde" }
            val safe = label.take(75)

            // Skulle den mot formodning bli tom (etter trimming), dropp den
            if (safe.isEmpty()) null
            else OptionObject.builder()
                .text(
                    PlainTextObject.builder()
                        .text(safe)
                        .emoji(false)
                        .build()
                )
                .value("project_experience-${pe._id}")
                .build()
        }

    val options = (keyQualificationOptionElements + projectExperienceOptionElements)

    // Hvis du kan ende med 0 alternativer, gi en tekstlig feilmelding i stedet for en tom select
    if (options.isEmpty()) {
        return SectionBlock.builder()
            .text(PlainTextObject.builder().text("Fant ingen seksjoner å vurdere. Er CV-en deaktivert/uten innhold?").emoji(false).build())
            .build()
    }

    return SectionBlock.builder()
        .text(PlainTextObject.builder().text(whichSectionQuestion).emoji(true).build())
        .accessory(
            StaticSelectElement.builder()
                .placeholder(PlainTextObject.builder().text("Velg").emoji(true).build())
                .options(options)
                .build()
        )
        .build()
}

private fun getProjectDescription(pe: FlowcaseService.ProjectExperiences): String {
    return ("**Prosjekt:** " + getText_NO(pe.description) + "\\\n" +
            "**Kunde:** " + getText_NO(pe.customer) + "\\\n" +
            "**Periode:** " + getDuration(pe) + "\n\n" +
            "**Prosjektbeskrivelse:** " + getText_NO(pe.long_description) + "\n\n" +

            pe.roles.joinToString("\n\n") { r ->
                "**Rolle:** " + getText_NO(
                    r.name
                ) + "\\\n" +
                        "**Rollebeskrivelse:** " + getText_NO(r.long_description)
            } + "\n\n" +
            "**Teknologi/Verktøy/Metode:** " + ((pe.project_experience_skills?.map { it.tags.no }
        ?.filter { !it.isNullOrBlank() }?.joinToString().takeIf { !it.isNullOrBlank() }) ?: "<IKKE OPPGITT>"))
}

private fun getText_NO(tv: FlowcaseService.TranslatedValue): String {
    return tv.no?.takeIf { it.isNotEmpty() }
        ?: "<IKKE OPPGITT>"
}

private fun getOptionText_NO(pe: FlowcaseService.ProjectExperiences): String {
    return (pe.description.no
        ?.takeIf { it.isNotEmpty() }
        ?: "Ikke-navngitt prosjekt") + " - " +
            (pe.customer.no
                ?.takeIf { it.isNotEmpty() }
                ?: "Ikke-navngitt kunde")
}

private fun getDuration(pe: FlowcaseService.ProjectExperiences): String {
    return "${
        (pe.takeIf { !it.month_from.isNullOrBlank() && !it.year_from.isNullOrBlank() }
            ?.let { it.month_from + "." + it.year_from }) ?: "<IKKE OPPGITT>"
    } - ${
        (pe.takeIf { !it.month_to.isNullOrBlank() && !it.year_to.isNullOrBlank() }
            ?.let { it.month_to + "." + it.year_to }) ?: "d.d."
    }"
}

private fun getButtonText(projectExperience: FlowcaseService.ProjectExperiences): String {
    return projectExperience.description.no
        ?.takeIf { it.isNotEmpty() }
        ?.take(72)
        ?: "Ikke-navngitt prosjekt"
}

annotation class NoArgConstructor

@NoArgConstructor
data class FirestoreThread(
    val userEmail: String,
    val openAiThreadId: String? = null,
    val expiresAt: Timestamp = Timestamp.of(Date.from(ZonedDateTime.now().plusHours(8).toInstant()))
)

private fun writeEmailToDatastore(slackThread: SlackThread, userEmail: String) {
    val id = firestoreId(slackThread)
    log.debug { "Writing to firestore: id=$id" }
    val result = firestore.collection("threads").document(id).set(FirestoreThread(userEmail)).get()
    log.info { "Wrote to firestore: $result" }
}

private fun updateFirestoreWithOpenAiThreadId(slackThread: SlackThread, openAiThreadId: String) {
    val id = firestoreId(slackThread)
    log.debug { "Writing to firestore: id=$id" }
    val patch = mapOf("openAiThreadId" to openAiThreadId)
    val result = firestore.collection("threads").document(id).set(patch, SetOptions.merge()).get()
    log.info { "Wrote to firestore: $result" }
}

private fun readFromFirestore(slackThread: SlackThread): FirestoreThread? {
    val document = firestore.collection("threads").document(firestoreId(slackThread)).get().get()
    if (!document.exists()) {
        return null
    }
    return document.toObject(FirestoreThread::class.java)!!
}

private fun firestoreId(slackThread: SlackThread) = "${slackThread.channelId}#${slackThread.threadTs}"

fun getEnvVariableOrThrow(variableName: String) = System.getenv()[variableName]
    ?: throw Exception("$variableName not defined in environment variables")
