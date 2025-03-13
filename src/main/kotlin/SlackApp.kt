package no.jpro.slack.cv

import com.slack.api.bolt.App
import com.slack.api.bolt.context.builtin.EventContext
import com.slack.api.methods.response.users.UsersInfoResponse
import com.slack.api.model.Message
import com.slack.api.model.event.MessageEvent
import io.github.oshai.kotlinlogging.KotlinLogging

private const val CUSTOM_EVENT_TYPE = "cv_lest"

private const val OPENAI_THREAD = "openai_thread_id"
private val log = KotlinLogging.logger {}

class SlackApp(val app: App = App()) {
    private val openAIClient = SimpleOpenAIClient()

    init {
        // Rekkefølge betyr noe...
        slashCommantLesCV()
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


    private fun slashCommantLesCV() {
        app.command("/lescv") { req, ctx ->
            val payload = req.payload
            log.debug { "Slash command  /lescv" }
            app.executorService().submit {
                //                ctx.respond{it.text("Vent ett øyeblikk mens jeg laster ned CV og gjør ting klart.")}
                ctx.respond { it.text("Jeg forteller en vits i stedet") }
                openAIClient.startNewThread(
                    message = "Fortell en vits",
                    onAnswer = { answer, openAiThread ->
                        ctx.say {
                            it
                                .text(answer)
                                .channel(payload.userId)
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
            ctx.ack("Ok leser deg klart og tydelig. Sjekker cv for ${payload.userName}")
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

    private fun getUserInfo(event: MessageEvent): UsersInfoResponse? {
        val usersInfo = app.client.usersInfo { it.user(event.user) }
        return usersInfo
    }
}

