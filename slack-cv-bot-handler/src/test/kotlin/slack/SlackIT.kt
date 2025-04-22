package slack

import com.slack.api.Slack
import com.slack.api.methods.kotlin_extension.request.chat.blocks
import no.jpro.slack.cv.SectionDetails
import no.jpro.slack.cv.markdownQuoteBlock
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

@Disabled
class SlackIT {

    private val slackBotToken = "replace-me" // starts with 'xoxb-'
    private val slack = Slack.getInstance().methods(slackBotToken)
    private val channelId = "C08LWCP0LTT" // #cv-prat-test

    @Test
    fun testMarkdown() {
        val multilineDescription = """
            Prosjekt 1 var kult.
            Jeg fikset ting.
            Julen var hvit det året.
        """.trimIndent()
        val sectionDetails = SectionDetails("ABC", "Hei?", multilineDescription)

        val result = slack.chatPostMessage {
            val text = "Sender *${sectionDetails.title}* til OpenAI for vurdering"
            it
                .channel(channelId)
                .text(text)
                .blocks {
                    section { markdownText(text) }
                    sectionDetails.description?.let { description -> section { markdownText(description.markdownQuoteBlock()) } }
                }
        }

        assertTrue(result.isOk)
    }
}
