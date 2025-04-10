package slack

import com.slack.api.Slack
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

@Disabled
class SlackIT {

    private val slackBotToken = "replace-me" // starts with 'xoxb-'
    private val slack = Slack.getInstance().methods(slackBotToken)
    private val channelId = "C08LWCP0LTT" // #cv-prat-test

    @Test
    fun testMarkdown() {
        slack.chatPostMessage {
            it.channel(channelId)
                .text("*Bold*")
        }
    }
}
