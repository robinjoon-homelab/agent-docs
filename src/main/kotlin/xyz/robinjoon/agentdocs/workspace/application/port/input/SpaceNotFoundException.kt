package xyz.robinjoon.agentdocs.workspace.application.port.input

// Also used when the space exists but the caller may not see it, so its existence never leaks.
class SpaceNotFoundException(
    reference: Any,
    cause: Throwable? = null,
) : RuntimeException("space not found: $reference", cause)
