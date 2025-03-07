package no.jpro.slack.cv

import com.slack.api.app_backend.slash_commands.payload.SlashCommandPayload
import com.slack.api.bolt.App
import com.slack.api.bolt.context.builtin.EventContext
import com.slack.api.bolt.context.builtin.SlashCommandContext
import com.slack.api.methods.response.users.UsersInfoResponse
import com.slack.api.model.Message
import com.slack.api.model.event.MessageEvent
import kotlinx.coroutines.delay

private const val CUSTOM_EVENT_TYPE = "cv_lest"

private const val OPENAI_THREAD = "openai_thread_id"

class SlackApp(val app: App = App()) {
    init {

        app.command("/lescv") { req, ctx ->
            val payload = req.payload
            app.executorService().submit {
//                ctx.respond{it.text("Vent ett øyeblikk mens jeg laster ned CV og gjør ting klart.")}
                ctx.respond{it.text("Jeg forteller en vits i stedet")}
                OpenAIClient().chat(
                    message = "Fortell en vits",
                    onAnswer = {answer, openAiThread ->
                        initialMessage(
                            ctx = ctx,
                            payload,
                            openAiThread,
                            message = answer
                        )
                    }
                )
//                sendMessage(
//                    ctx,
//                    payload,
//                    message = "Da er jeg klar for å snakke om cv'n. Skriv ha du lurer på i tråden under v3"
//                )
//                ctx.logger.info("respond: $resp")
            }
            ctx.ack("Ok leser deg klart og tydelig. Sjekker cv for ${payload.userName}")
        }


        app.event(MessageEvent::class.java) { payload, ctx ->
            val event = payload.event
            app.executorService().submit {
                replyMessage(event, ctx)
            }
            ctx.ack()
        }

    }

    private fun initialMessage(
        ctx: SlashCommandContext,
        payload: SlashCommandPayload,
        openAiThread: String? = null,
        message: String
    ): String? {
        val resp = ctx.say {
            it
                .text(message)
                .channel(payload.userId)
                .metadata(
                    Message.Metadata.builder()
                        .eventType(CUSTOM_EVENT_TYPE)
                        .eventPayload(mapOf(OPENAI_THREAD to openAiThread))
                        .build()
                )
        }
        return resp.ts
    }

    private suspend fun sendHello(
        ctx: EventContext,
        event: MessageEvent
    ) {
        delay(5000)
        ctx.logger.info("Sier hallo ${ctx.retryNum}")
        ctx.say {
            it
                .text("Hallo ${ctx.retryNum}")
                .threadTs(event.threadTs ?: event.ts)
                .channel(event.channel)
        }
    }


    private fun replyMessage(
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
        ctx.logger.info("replies metadata {}", openAiThread)
        OpenAIClient().chat(
            message = event.text,
            threadId = openAiThread,
            onAnswer = {answer, threadId ->
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

    private fun getUserInfo(event: MessageEvent): UsersInfoResponse? {
        val usersInfo = app.client.usersInfo { it.user(event.user) }
        return usersInfo
    }
}

