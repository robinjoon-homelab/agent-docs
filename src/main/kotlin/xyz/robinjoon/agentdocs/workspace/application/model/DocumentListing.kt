package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.Actor
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentHeader
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentLifecycle

// A document in a list, without its blocks, with whoever made its latest revision.
data class DocumentListing(
    val id: DocumentId,
    val header: DocumentHeader,
    val lifecycle: DocumentLifecycle,
    val updatedBy: Actor,
)
