package xyz.robinjoon.agentdocs.workspace.domain.editsession

import xyz.robinjoon.agentdocs.workspace.domain.Actor
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import java.util.UUID

data class EditSessionTarget(
    val documentId: DocumentId,
    val spaceId: SpaceId,
    val actor: Actor,
    val tabId: UUID?,
) {
    init {
        require(actor.isAgent == (tabId == null)) { "A human session needs a tab ID" }
    }

    val scope: String get() = "${documentId.value}:${spaceId.value}:${actor.userId}:${actor.agent?.connectionId ?: tabId}"
}
