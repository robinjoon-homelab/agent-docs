package xyz.robinjoon.agentdocs.workspace.domain.document

import xyz.robinjoon.agentdocs.workspace.domain.SpaceId

// The metadata shown as front matter: the space, the path inside it, the title, and the labels.
data class DocumentHeader(
    val spaceId: SpaceId,
    val path: DocumentPath,
    val title: String,
    val properties: DocumentProperties,
) {
    init {
        if (title.isBlank() || title.length > MAX_TITLE_LENGTH || title != title.trim() || title.contains('\n')) {
            throw InvalidDocumentException("title must be one trimmed line of 1 to $MAX_TITLE_LENGTH characters")
        }
    }

    companion object {
        const val MAX_TITLE_LENGTH = 200
    }
}
