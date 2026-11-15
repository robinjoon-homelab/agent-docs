package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId

// Where a document a space names as its guide is now, read without its blocks.
data class GuideCandidate(
    val id: DocumentId,
    val spaceId: SpaceId,
    val title: String,
    val path: String,
    val trashed: Boolean,
)
