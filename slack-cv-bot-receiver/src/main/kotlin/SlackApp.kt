package no.jpro.slack.cv

import com.slack.api.bolt.App
import io.github.oshai.kotlinlogging.KotlinLogging

private val log = KotlinLogging.logger {}

class SlackApp(val app: App = App()) {

    init {
        slashCommandLesCV()
        pingMessage()
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
            val say = ctx.say {
                it.channel(req.payload.channelId)
                    .text("OK, leser deg klart og tydelig. Sjekker CV for ${req.payload.userName}")
            }
            val userEmail = getUserEmail(req.payload.userId)
            if (userEmail == null) {
                ctx.say { it.threadTs(say.ts).text("Fant ikke epost for ${req.payload.userName}") }
                return@command ctx.ack()
            }
            log.debug { "Building message" }
            val slashCommand = SlackSlashCommand(say.ts, userEmail)
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

    private fun getUserEmail(userid: String): String? {
        val usersInfo = app.client.usersInfo { it.user(userid) }
        return usersInfo?.user?.profile?.email
    }
}
