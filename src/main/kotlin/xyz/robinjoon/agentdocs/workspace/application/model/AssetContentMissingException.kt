package xyz.robinjoon.agentdocs.workspace.application.model

// The asset row exists but its bytes are not in the store, for example after the bucket was emptied by hand.
class AssetContentMissingException(
    sha256: String,
    cause: Throwable? = null,
) : RuntimeException("no stored bytes for content $sha256", cause)
