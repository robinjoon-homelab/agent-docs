package xyz.robinjoon.agentdocs.workspace.application.port.input

// Also used when the document exists but the caller may not see it, so its existence never leaks.
class DocumentNotFoundException(
    reference: Any,
) : RuntimeException("document not found: $reference")
