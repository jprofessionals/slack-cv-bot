package no.jpro.slack.cv

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
@JvmRecord
data class PubSubBody(val message: PubsubMessage) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JvmRecord
    data class PubsubMessage(
        val data: String,
        val attributes: Map<String, String> = emptyMap(),
        val messageId: String,
        val publishTime: String
    )
}
