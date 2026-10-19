package xyz.robinjoon.agentdocs.workspace.application.port.input

// Also used when the revision exists but the caller may not see it, so its existence never leaks.
class RevisionNotFoundException(
    reference: Any,
) : RuntimeException("revision not found: $reference")
