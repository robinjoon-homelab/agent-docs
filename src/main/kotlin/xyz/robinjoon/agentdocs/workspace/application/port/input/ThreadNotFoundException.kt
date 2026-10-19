package xyz.robinjoon.agentdocs.workspace.application.port.input

// Also used when the thread exists but the caller may not see it, so its existence never leaks.
class ThreadNotFoundException(
    reference: Any,
) : RuntimeException("thread not found: $reference")
