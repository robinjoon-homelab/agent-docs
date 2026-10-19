package xyz.robinjoon.agentdocs.workspace.domain.document.history

import xyz.robinjoon.agentdocs.workspace.domain.Actor
import xyz.robinjoon.agentdocs.workspace.domain.document.ChangeSource

data class ChangeOrigin(
    val actor: Actor,
    val source: ChangeSource,
)
