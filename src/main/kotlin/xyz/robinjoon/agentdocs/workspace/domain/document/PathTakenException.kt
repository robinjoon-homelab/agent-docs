package xyz.robinjoon.agentdocs.workspace.domain.document

// path is the one in use, which may be where a document moved along with the named one would have landed.
class PathTakenException(
    val path: DocumentPath,
    cause: Throwable? = null,
) : RuntimeException("another document in the space already has the path $path", cause)
