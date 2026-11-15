package xyz.robinjoon.agentdocs.workspace.domain.document.history

import xyz.robinjoon.agentdocs.workspace.domain.document.InvalidDocumentException

// The importer describes the source, while the authenticated actor remains the author.
data class ImportProvenance(
    val tool: String,
    val originalPath: String,
) {
    init {
        if (tool.isBlank() || tool.length > 100 || originalPath.isBlank() || originalPath.length > 1000) {
            throw InvalidDocumentException("import provenance needs a tool and original relative path")
        }
        if (originalPath.startsWith('/') || Regex("^[A-Za-z]:").containsMatchIn(originalPath) || originalPath.contains('\\') ||
            originalPath.split('/').any { it == ".." }
        ) {
            throw InvalidDocumentException("import provenance path must be relative without parent traversal")
        }
    }
}
