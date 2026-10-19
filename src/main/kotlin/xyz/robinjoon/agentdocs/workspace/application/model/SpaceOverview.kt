package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.space.Space
import java.time.Instant

data class SpaceOverview(
    val space: Space,
    val documentCount: Int,
    val lastActivityAt: Instant?,
)
