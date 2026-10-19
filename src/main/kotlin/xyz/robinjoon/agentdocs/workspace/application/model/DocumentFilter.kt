package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentStatus

// folder limits the list to documents whose path starts with that folder.
data class DocumentFilter(
    val status: DocumentStatus?,
    val tag: String?,
    val folder: String?,
    val trashed: Boolean,
) {
    companion object {
        val active = DocumentFilter(null, null, null, false)
    }
}
