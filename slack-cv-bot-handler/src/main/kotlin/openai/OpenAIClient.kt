package no.jpro.slack.cv.openai

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.sashirestela.cleverclient.Event
import io.github.sashirestela.openai.SimpleOpenAI
import io.github.sashirestela.openai.common.content.ContentPart
import io.github.sashirestela.openai.domain.assistant.ThreadMessage
import io.github.sashirestela.openai.domain.assistant.ThreadMessageRequest
import io.github.sashirestela.openai.domain.assistant.ThreadMessageRole
import io.github.sashirestela.openai.domain.assistant.ThreadRunRequest
import io.github.sashirestela.openai.domain.assistant.events.EventName
import java.util.stream.Stream

private val log = KotlinLogging.logger {}

class OpenAIClient(
    apiKey: String = System.getenv("OPENAI_API_KEY"),
    private val assistantId: String = "asst_YvsGiwk68CZ5wUKcNquxDmHz"
) {
    private val openAI: SimpleOpenAI

    init {
        if (apiKey.isEmpty()) {
            throw IllegalArgumentException("OpenAI token is required in env property OPENAI_API_KEY")
        }
        openAI = SimpleOpenAI.builder()
            .apiKey(apiKey)
            .build();
    }

    fun startNewThread(message: String, onAnswer: (answer: String, threadId: String) -> Unit): String {
        val openAiThreadId = createThread()
        chatInThread(message, openAiThreadId, onAnswer)
        return openAiThreadId
    }

    fun chatInThread(
        message: String,
        openAiThreadId: String,
        onAnswer: (answer: String, threadId: String) -> Unit,
        role: ThreadMessageRole=ThreadMessageRole.USER
    ) {
        log.debug { "Sending message to thread $openAiThreadId" }
        openAI.threadMessages()
            .create(
                openAiThreadId, ThreadMessageRequest.builder()
                    .role(role)
                    .content(message)
                    .build()
            )
            .join()
        log.debug { "Listen for message to thread $openAiThreadId" }
        val runStream = openAI.threadRuns()
            .createStream(
                openAiThreadId, ThreadRunRequest.builder()
                    .assistantId(assistantId)
                    .build()
            )
            .join()
        handleRunEvents(
            runStream,
            onReply = { reply: String -> onAnswer(reply, openAiThreadId) },
        )
    }

    private fun createThread(): String {
        log.debug { "Create thread" }
        return openAI.threads().create().join().id
    }


    private fun handleRunEvents(runStream: Stream<Event>, onReply: (answer: String) -> Unit) {
        runStream.forEach { event ->
            when (event.getName()) {

                EventName.THREAD_MESSAGE_DELTA -> {
                }

                EventName.THREAD_MESSAGE_COMPLETED -> {
                    val message = event.data as ThreadMessage
                    val content =
                        message.content[0]
                    if (content is ContentPart.ContentPartTextAnnotation) {
                        onReply(content.text.value)
                    }
                }

                else -> {}
            }
        }
    }

}
