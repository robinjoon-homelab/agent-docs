package xyz.robinjoon.agentdocs.workspace.application.model

class AssetStorageException(
    cause: Throwable,
) : RuntimeException("asset storage failed", cause)
