package xyz.robinjoon.agentdocs.workspace.domain.thread

import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId

// The document a thread belongs to and where in it, with the revision the anchor was taken from.
data class ThreadLocation(
    val spaceId: SpaceId,
    val documentId: DocumentId,
    val anchor: Anchor,
    val anchoredRevision: Int,
)
