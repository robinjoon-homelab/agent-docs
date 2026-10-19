package xyz.robinjoon.agentdocs.workspace.application.model

import java.time.Instant

// Log entries after a moment, oldest first, and the moment to ask from next time.
data class ChangeFeed(
    val entries: List<ActivityView>,
    val nextSince: Instant,
)
