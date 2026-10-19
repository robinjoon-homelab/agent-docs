package xyz.robinjoon.agentdocs.workspace.application.model

// A request the use case cannot carry out as written, with a hint on how to fix it.
open class InvalidRequestException(
    message: String,
    val fix: String? = null,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)
