package xyz.robinjoon.agentdocs.workspace.domain.document

class InvalidDocumentException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)
