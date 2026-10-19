package xyz.robinjoon.agentdocs.workspace.domain.document

class PathTakenException(
    path: DocumentPath,
    cause: Throwable? = null,
) : RuntimeException("another document in the space already has the path $path", cause)
