package xyz.robinjoon.agentdocs.workspace.application.model

import java.time.Instant

data class SpaceStatistics(
    val documentCount: Int,
    val lastActivityAt: Instant?,
)
