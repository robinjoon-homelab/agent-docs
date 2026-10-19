package xyz.robinjoon.agentdocs.workspace.application.port.input

// Also used when the inbox item exists but the caller may not see it, so its existence never leaks.
class InboxItemNotFoundException(
    reference: Any,
) : RuntimeException("inbox item not found: $reference")
