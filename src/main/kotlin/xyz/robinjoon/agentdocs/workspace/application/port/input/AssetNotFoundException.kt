package xyz.robinjoon.agentdocs.workspace.application.port.input

// Also used when the asset exists but the caller may not see it, so its existence never leaks.
class AssetNotFoundException(
    reference: Any,
) : RuntimeException("asset not found: $reference")
