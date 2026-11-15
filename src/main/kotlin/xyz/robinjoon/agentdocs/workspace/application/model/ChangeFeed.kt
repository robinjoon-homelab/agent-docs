package xyz.robinjoon.agentdocs.workspace.application.model

import java.time.Instant

// Cursor consumers reuse initialSince with nextCursor. nextSince preserves timestamp-only legacy continuation.
data class ChangeFeed(
    val entries: List<ActivityView>,
    val nextSince: Instant,
    val nextCursor: Long?,
    val initialSince: Instant,
)
