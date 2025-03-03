package no.jpro.slack.cv

import com.slack.api.bolt.App
import com.slack.api.bolt.context.builtin.EventContext
import com.slack.api.methods.response.users.UsersInfoResponse
import com.slack.api.model.Message
import com.slack.api.model.event.AppMentionEvent
import com.slack.api.model.event.MessageEvent

class SlackApp(val app: App = App()) {
    init {

        app.command("/lescv") { req, ctx ->
            val payload = req.payload
            val metadata = mapOf("openai_ass_id" to "123123", "dill" to "dall")
            app.executorService().submit {
                Thread.sleep(1000)
                val resp=ctx.say {
                    it
                        .text("Da er jeg klar for å snakke om cv'n. Skriv ha du lurer på i tråden under v2")
                        .channel(payload.userId)
                        .metadata(
                            Message.Metadata.builder()
                                .eventType("cv_lest")
                                .eventPayload(metadata)
                                .build()
                        )
                }
                ctx.logger.info("respond: $resp")
            }
            ctx.ack("Ok leser deg klart og tydelig. Sjekker cv for ${payload.userName}")
        }

        app.message("Test") { message, ctx ->
//            ctx.threadTs=message.event.threadTs

            ctx.say {
                it.text("Hello Slack!")
                    .threadTs(message.event.ts)
            }
            ctx.ack()
        }


        app.event(AppMentionEvent::class.java) { payload, ctx ->
            ctx.say {
                it
                    .threadTs(payload.event.ts)
                    .text("<@${payload.event.user}> What's up?")
            }
            ctx.ack()
        }


        app.event(MessageEvent::class.java) { payload, ctx ->
            val event = payload.event
            app.executorService().submit {
                onMessage(event, ctx)
            }
            ctx.ack()
        }

    }


    private fun onMessage(
        event: MessageEvent,
        ctx: EventContext
    ) {
        val usersInfo = getUserInfo(event)
        ctx.logger.info("event result - ts {} threadts {} channel {}", event.ts, event.threadTs, event.channel)
//        ctx.say("<@${event.user}> / ${usersInfo?.user?.profile?.email} Jeg forstår ikke hva du mener")
        val replies = history(event)
//        ctx.logger.info("replies {}", replies?.map { "${it.user}  ${it.text}\n" })
        ctx.logger.info("replies metadata {}", replies?.map { it.metadata })

        ctx.say {
            it
                .text("jasså du sier ${event.text}")
                .threadTs(event.threadTs ?: event.ts)
                .channel(event.channel)
        }
    }

    private fun history(event: MessageEvent): List<Message>? {

        val replies = app.client.conversationsReplies {
            it.ts(event.threadTs)
                .channel(event.channel)
                .includeAllMetadata(true)
        }
        return replies.messages;
    }

    private fun getUserInfo(event: MessageEvent): UsersInfoResponse? {
        val usersInfo = app.client.usersInfo { it.user(event.user) }
        return usersInfo
    }
}

