package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId

data class RevisionLabel(
    val documentId: DocumentId,
    val revision: Int,
    val name: String?,
)
