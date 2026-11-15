package xyz.robinjoon.agentdocs.workspace.domain.document.history

import xyz.robinjoon.agentdocs.workspace.domain.document.InvalidDocumentException

object RevisionName {
    fun normalize(value: String): String? {
        val name = value.trim()
        if (name.length > 100 || name.contains('\n') || name.contains('\r')) {
            throw InvalidDocumentException("revision name must be one line of at most 100 characters")
        }
        return name.ifEmpty { null }
    }
}
