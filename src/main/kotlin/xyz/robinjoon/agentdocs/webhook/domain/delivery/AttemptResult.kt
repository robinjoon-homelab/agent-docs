package xyz.robinjoon.agentdocs.webhook.domain.delivery

import java.time.Duration

// What one request produced. Only a 2xx answer delivers; 410 Gone means the receiver wants no more and the endpoint
// stops; every other answer, including a redirect, which is never followed, is a failure to retry.
sealed interface AttemptResult {
    val succeeded: Boolean
    val gone: Boolean

    // retryAfter is the receiver's Retry-After in seconds when it sent one.
    data class Answered(
        val status: Int,
        val retryAfter: Duration?,
    ) : AttemptResult {
        override val succeeded: Boolean get() = status in success
        override val gone: Boolean get() = status == GONE
    }

    // No answer: a timeout, a refused or failed connection, or an address refused before connecting. error is a short
    // code such as timeout, never a message that could echo the address.
    data class Unanswered(
        val error: String,
    ) : AttemptResult {
        override val succeeded: Boolean get() = false
        override val gone: Boolean get() = false
    }
}

private val success = 200..299
private const val GONE = 410
