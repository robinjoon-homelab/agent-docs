package xyz.robinjoon.agentdocs.workspace.domain.document.history

import java.time.Instant

// When a revision was first written and when a person's continued typing last replaced it.
data class ChangePeriod(
    val createdAt: Instant,
    val updatedAt: Instant,
)
