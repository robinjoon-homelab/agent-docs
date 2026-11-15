package xyz.robinjoon.agentdocs.webhook.domain.endpoint

import java.time.Instant

// When the endpoint started, when its secret was last replaced, and whether it stopped. A stopped endpoint stays as a
// record; it is never deleted or resumed.
data class EndpointTimeline(
    val createdAt: Instant,
    val secretRotatedAt: Instant?,
    val stop: EndpointStop?,
)

data class EndpointStop(
    val at: Instant,
    val reason: StopReason,
)

// OWNER means the owner stopped it; GONE means the receiver answered 410 Gone.
enum class StopReason {
    OWNER,
    GONE,
}
