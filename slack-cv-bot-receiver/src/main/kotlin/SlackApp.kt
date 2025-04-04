package no.jpro.slack.cv

import com.slack.api.bolt.App
import io.github.oshai.kotlinlogging.KotlinLogging

private val log = KotlinLogging.logger {}

class SlackApp(val app: App = App()) {

    init {
        slashCommandLesCV()
        pingMessage()
        blockAction()
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
            log.info { "Slash command  /lescv" }
            val userEmail = getUserEmail(req.payload.userId)
            if (userEmail == null) {
                ctx.respond("Jeg fant ikke epost for ${req.payload.userName}")
                return@command ctx.ack()
            }
            val say = ctx.say {
                it.channel(req.payload.channelId)
                    .text("Jeg sjekker CV for ${req.payload.userName} (${userEmail})")
            }
            if (!say.isOk) {
                ctx.respond("Jeg har ikke tilgang til å sende fullstendige svar til kanalen. Jeg må være medlem. (${say.error})")
                return@command ctx.ack()
            }
            log.debug { "say.ts=${say.ts}" }
            log.debug { "Building message" }
            val slashCommand = SlackSlashCommand(SlackThread(say.channel, say.ts), userEmail)
            val message = pubsubMessage(slashCommand)
            log.debug { "Ready to publish message" }
            try {
                val messageId = send(message)
                log.info { "Message published to PubSub, id=$messageId" }
            } catch (e: Exception) {
                log.error(e) { "Message send failed" }
            }
            ctx.ack()
        }
    }

    private fun blockAction() {
        app.blockAction(".*") { req, ctx ->
            log.debug { "req=$req, ctx=$ctx" }
            ctx.ack()
        }
    }

    private fun getUserEmail(userid: String): String? {
        val usersInfo = app.client.usersInfo { it.user(userid) }
        return usersInfo?.user?.profile?.email
    }
}
