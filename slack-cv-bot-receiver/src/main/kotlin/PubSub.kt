package no.jpro.slack.cv

import com.google.cloud.pubsub.v1.Publisher
import com.google.protobuf.ByteString
import com.google.pubsub.v1.PubsubMessage
import com.google.pubsub.v1.TopicName
import no.jpro.slack.SlackEvent
import org.apache.avro.io.EncoderFactory
import org.apache.avro.specific.SpecificDatumWriter
import java.io.ByteArrayOutputStream

private val projectId: String = getEnvVariableOrThrow("GOOGLE_CLOUD_PROJECT_NAME")
private val pubsubEndpoint: String = getEnvVariableOrDefault("PUBSUB_ENDPOINT", "europe-west1-pubsub.googleapis.com:443")
private val topicId = "slack-events"
private val topicName = TopicName.of(projectId, topicId)
private val publisher: Publisher =
    Publisher.newBuilder(topicName)
        .setEndpoint(pubsubEndpoint)
        .setEnableMessageOrdering(true)
        .build()

private val slackEventWriter = SpecificDatumWriter(SlackEvent::class.java)

fun send(message: PubsubMessage): String = publisher.publish(message).get()

fun <T> pubsubMessage(content: T): PubsubMessage {
    val event = SlackEvent.newBuilder().setEvent(content).build()
    return PubsubMessage.newBuilder()
        .setData(ByteString.copyFrom(encodeToAvro(event)))
        .build()
}

private fun encodeToAvro(event: SlackEvent): ByteArray {
    val byteStream = ByteArrayOutputStream()
    val encoder = EncoderFactory.get().directBinaryEncoder(byteStream, null)
    slackEventWriter.write(event, encoder)
    encoder.flush()
    return byteStream.toByteArray()
}
