package no.jpro.slack.cv

import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.assistant.AssistantId
import com.aallam.openai.api.core.Role
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.message.Message
import com.aallam.openai.api.message.MessageContent
import com.aallam.openai.api.message.MessageRequest
import com.aallam.openai.api.run.AssistantStreamEvent
import com.aallam.openai.api.run.AssistantStreamEventType
import com.aallam.openai.api.run.RunRequest
import com.aallam.openai.api.thread.ThreadId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.extension.getData
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds

@OptIn(BetaOpenAI::class)
class OpenAIClient(
    api_key: String = System.getenv("OPENAI_API_KEY"),
    private val assistantId: String = "asst_YvsGiwk68CZ5wUKcNquxDmHz"
) {
    private val openAI: OpenAI

    init {
        if (api_key.isEmpty()) {
            throw IllegalArgumentException("OpenAI token is required in env property OPENAI_API_KEY")
        }
        openAI = OpenAI(
            token = api_key,
            timeout = Timeout(socket = 60.seconds),
        )
    }

    fun chat(message: String, threadId: String? = null, onAnswer:(answer: String, threadId: String)-> Unit) {
        runBlocking {
            val thread = if (threadId != null) ThreadId(threadId) else openAI.thread().id
//        val thread = openAI.thread(threadId)

            openAI.message(
                threadId = thread,
                request = MessageRequest(
                    role = Role.User,
                    content = message
                )
            )
            openAI.createStreamingRun(threadId=thread, request = RunRequest(
                assistantId = AssistantId(assistantId)
            ))
                .filter { event -> event.type==AssistantStreamEventType.THREAD_MESSAGE_COMPLETED }
                .map { it.getData<Message>() }
                .onEach { message -> onAnswer(parseMessage(message), message.threadId.id) }
                .collect()
//            val run = openAI.createRun(
//                threadId = thread,
//                request = RunRequest(
//                    assistantId = AssistantId(assistantId)
//
//                )
//            )
//            // 6. Check the run status
//            do {
//                delay(1500)
//                val retrievedRun = openAI.getRun(threadId = thread, runId = run.id)
//            } while (retrievedRun.status != Status.Completed)
//
//            // 6. Display the assistant's response
//            val assistantMessages = openAI.messages(thread)
////            println("\nThe assistant's response:")
//            assistantMessages.first()
////            for (message in assistantMessages) {
////                val textContent =
////                    message.content.first() as? MessageContent.Text ?: error("Expected MessageContent.Text")
////                println(textContent.text.value)
////            }

        }
    }

    private fun parseMessage(message: Message): String {
        val textContent = message.content.first() as? MessageContent.Text ?: error("Expected MessageContent.Text")
        return textContent.text.value
    }

    private fun handleEvent(event: AssistantStreamEvent) {

        when (event.type) {
            AssistantStreamEventType.THREAD_MESSAGE_COMPLETED -> {
                val message = event.getData<Message>()
                println(message)
            }

            else -> {//ignore}
            }

        }

    }
}