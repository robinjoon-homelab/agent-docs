package xyz.robinjoon.agentdocs.access.domain.session

import java.time.Instant

data class SessionTerm(
    val startedAt: Instant,
    val lastUsedAt: Instant,
)
