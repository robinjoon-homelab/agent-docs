package xyz.robinjoon.agentdocs.workspace.domain.inbox

import java.time.Instant

data class Delivery(
    val deliveredAt: Instant,
    val readAt: Instant?,
)
