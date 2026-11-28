package xyz.robinjoon.agentdocs.application.model

import java.time.Instant

data class ActivityFilter(
    val kind: String? = null,
    val actorKind: ActivityActorKind? = null,
    val since: Instant? = null,
)
