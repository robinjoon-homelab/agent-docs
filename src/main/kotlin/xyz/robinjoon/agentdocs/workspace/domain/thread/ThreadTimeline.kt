package xyz.robinjoon.agentdocs.workspace.domain.thread

import xyz.robinjoon.agentdocs.workspace.domain.Actor
import java.time.Instant

data class ThreadTimeline(
    val createdAt: Instant,
    val updatedAt: Instant,
    val resolvedAt: Instant?,
    val resolvedBy: Actor?,
)
