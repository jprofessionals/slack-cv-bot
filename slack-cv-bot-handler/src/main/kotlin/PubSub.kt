package no.jpro.slack.cv

import com.fasterxml.jackson.module.kotlin.readValue
import com.google.cloud.pubsub.v1.AckReplyConsumer
import com.google.cloud.pubsub.v1.Subscriber
import com.google.pubsub.v1.ProjectSubscriptionName
import com.google.pubsub.v1.PubsubMessage
import io.github.oshai.kotlinlogging.KotlinLogging

private val log = KotlinLogging.logger {}

private val projectId: String = getEnvVariableOrThrow("GOOGLE_CLOUD_PROJECT_NAME")
private val subscriptionId: String = getEnvVariableOrThrow("SUBSCRIPTION_ID")
private val subscriptionName = ProjectSubscriptionName.of(projectId, subscriptionId)

fun receive(onEvent: (slackSlashCommand: SlackSlashCommand) -> Unit) {
    val subscriber = Subscriber.newBuilder(subscriptionName) { message: PubsubMessage, ackReplyConsumer: AckReplyConsumer ->
        log.info { "Processing message with messageId=${message.messageId}" }
        val jsonString = message.data.toStringUtf8()
        onEvent(objectMapper.readValue(jsonString))
        ackReplyConsumer.ack()
    }.build()
    subscriber.startAsync().awaitRunning()
    log.info { "Listening for messages on $subscriptionName" }
    subscriber.awaitTerminated()
}
