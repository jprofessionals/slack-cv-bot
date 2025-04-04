package no.jpro.slack.cv

data class SlackSlashCommand (
    val slackThread: SlackThread,
    val userEmail: String,
)

data class SlackThread (
    val channelId: String,
    val threadTs: String,
)
