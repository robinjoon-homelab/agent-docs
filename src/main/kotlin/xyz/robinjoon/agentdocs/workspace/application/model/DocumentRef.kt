package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath

data class DocumentRef(
    val id: DocumentId,
    val path: DocumentPath,
    val title: String,
)
