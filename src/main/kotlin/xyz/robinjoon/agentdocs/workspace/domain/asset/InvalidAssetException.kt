package xyz.robinjoon.agentdocs.workspace.domain.asset

class InvalidAssetException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)
