package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug

data class DocumentTransfer(
    val space: SpaceSlug,
    val path: DocumentPath,
    val baseVersion: Int,
)
