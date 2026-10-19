package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.ActorKind
import java.time.Instant

data class RevisionFilter(
    val actorKind: ActorKind?,
    val from: Instant?,
    val to: Instant?,
)
