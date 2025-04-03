package no.jpro.slack.cv

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.cloud.pubsub.v1.Publisher
import com.google.protobuf.ByteString
import com.google.pubsub.v1.PubsubMessage
import com.google.pubsub.v1.TopicName

private val projectId: String = getEnvVariableOrThrow("GOOGLE_CLOUD_PROJECT_NAME")
private val pubsubEndpoint: String = getEnvVariableOrDefault("PUBSUB_ENDPOINT", "europe-west1-pubsub.googleapis.com:443")
private val topicId = "slack-events"
private val topicName = TopicName.of(projectId, topicId)
private val publisher: Publisher =
    Publisher.newBuilder(topicName)
        .setEndpoint(pubsubEndpoint)
        .setEnableMessageOrdering(true)
        .build()

private val objectMapper = jacksonObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .registerModule(JavaTimeModule())

fun send(message: PubsubMessage): String = publisher.publish(message).get()

fun pubsubMessage(slashCommand: SlackSlashCommand): PubsubMessage =
    PubsubMessage.newBuilder()
        .setData(ByteString.copyFromUtf8(objectMapper.writeValueAsString(slashCommand)))
        .build()
