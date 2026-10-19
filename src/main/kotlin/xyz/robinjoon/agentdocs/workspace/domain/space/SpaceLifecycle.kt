package xyz.robinjoon.agentdocs.workspace.domain.space

import java.time.Instant

// When a space was made and, while the owner keeps it archived, since when.
data class SpaceLifecycle(
    val createdAt: Instant,
    val archivedAt: Instant?,
)
