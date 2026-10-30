package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath

data class DocumentLocation(
    val spaceId: SpaceId,
    val path: DocumentPath,
)
