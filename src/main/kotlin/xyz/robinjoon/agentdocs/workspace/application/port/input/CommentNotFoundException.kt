package xyz.robinjoon.agentdocs.workspace.application.port.input

// Also used when the comment exists but the caller may not see it, so its existence never leaks.
class CommentNotFoundException(
    reference: Any,
) : RuntimeException("comment not found: $reference")
