package no.jpro.slack.cv

import com.google.pubsub.v1.PubsubMessage
import com.slack.api.app_backend.interactive_components.payload.BlockActionPayload
import com.slack.api.bolt.App
import com.slack.api.model.block.element.ButtonElement
import com.slack.api.model.block.element.StaticSelectElement
import io.github.oshai.kotlinlogging.KotlinLogging
import no.jpro.slack.SectionSelection
import no.jpro.slack.SectionType
import no.jpro.slack.SlackThread
import no.jpro.slack.SlashCommand
import no.jpro.slack.ThreadMessage
import java.util.regex.Pattern

private val log = KotlinLogging.logger {}

private val userMentionRegex = Pattern.compile("""^<@([UW][A-Z0-9]+)(\|[^>]+)>$""")

class SlackApp(val app: App = App()) {

    init {
        slashCommandLesCV()
        pingMessage()
        blockAction()
        message()
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

    private fun message() {
        app.message(Pattern.compile("^.*$")) { payload, ctx ->
            val channelId = payload.event.channel
            val threadTs = payload.event.threadTs
            val text = payload.event.text
            log.debug { "Received message channelId=$channelId, threadTs=$threadTs, text=$text" }
            if (threadTs != null) {
                val threadMessage = ThreadMessage(SlackThread(channelId, threadTs), text)
                val pubsubMessage = pubsubMessage(threadMessage)
                trySend(pubsubMessage)
            }
            ctx.ack()
        }
    }

    /** Starter en ny tråd i slack  */
    private fun slashCommandLesCV() {
        app.command("/lescv") { req, ctx ->
            log.info { "Slash command  /lescv. text=${req.payload.text}" }
            val (userToReview, userName) = if (req.payload.text != null) {
                val matcher = userMentionRegex.matcher(req.payload.text)
                if (matcher.matches()) {
                    Pair(matcher.group(1), matcher.group(2).removePrefix("|"))
                } else {
                    Pair(req.payload.userId, req.payload.userName)
                }
            } else {
                Pair(req.payload.userId, req.payload.userName)
            }
            log.info { "Using $userToReview $userName, invoked by ${req.payload.userId} ${req.payload.userName}" }
            val userEmail = getUserEmail(userToReview)
            if (userEmail == null) {
                ctx.respond("Jeg fant ikke epost for $userName")
                return@command ctx.ack()
            }
            val say = ctx.say {
                it.channel(req.payload.channelId)
                    .text("Jeg sjekker CV for $userName ($userEmail)")
            }
            if (!say.isOk) {
                ctx.respond("Jeg har ikke tilgang til å sende fullstendige svar til kanalen. Jeg må være medlem. (${say.error})")
                return@command ctx.ack()
            }
            log.debug { "say.ts=${say.ts}" }
            log.debug { "Building message" }
            val slashCommand = SlashCommand(SlackThread(say.channel, say.ts), userEmail)
            val message = pubsubMessage(slashCommand)
            log.debug { "Ready to publish message" }
            trySend(message)
            ctx.ack()
        }
    }

    private fun blockAction() {
        app.blockAction(Pattern.compile("^.*$")) { req, ctx ->
            log.debug { "req.payload.actions=${req.payload.actions},req.payload.message=${req.payload.message},req.payload.channel=${req.payload.channel}" }
            req.payload.actions.forEach { action ->
                log.debug { "Building message" }
                val (sectionTypeString, sectionId) = when(val parsedAction = parseAction(action)) {
                    null -> return@forEach
                    else -> parsedAction
                }
                val sectionType = SectionType.entries.first { it.name.contentEquals(sectionTypeString, true) }
                val sectionSelection = SectionSelection(SlackThread(req.payload.channel.id, req.payload.message.threadTs), sectionId, sectionType)
                val message = pubsubMessage(sectionSelection)
                trySend(message)
            }
            ctx.ack()
        }
    }

    private fun parseAction(action: BlockActionPayload.Action): Pair<String, String>? {
        val rawValue = when (action.type) {
            StaticSelectElement.TYPE -> action.selectedOption.value
            ButtonElement.TYPE -> action.value
            else -> {
                log.info { "unknown action type: ${action.type}" }
                return null
            }
        }
        val split = rawValue.split("-")
        return split[0] to split[1]
    }

    private fun trySend(message: PubsubMessage) {
        try {
            val messageId = send(message)
            log.info { "Message published to PubSub, id=$messageId" }
        } catch (e: Exception) {
            log.error(e) { "Message send failed" }
        }
    }

    private fun getUserEmail(userid: String): String? {
        val usersInfo = app.client.usersInfo { it.user(userid) }
        return usersInfo?.user?.profile?.email
    }
}
