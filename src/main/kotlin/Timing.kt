package no.jpro.slack.cv

import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Duration
import java.time.Instant

private val log = KotlinLogging.logger {}

fun <T> timing(label: String, function: () -> T): T {
    val start = Instant.now()
    val result = function()
    val timeTaken = Duration.between(start, Instant.now())
    log.debug { "$label completed in ${timeTaken.toMillis()} ms" }
    return result
}
