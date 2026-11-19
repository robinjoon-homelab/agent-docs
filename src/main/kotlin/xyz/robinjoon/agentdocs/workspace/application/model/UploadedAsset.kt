package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.asset.Asset

// The attachment and the original label used to build its Markdown reference.
data class UploadedAsset(
    val asset: Asset,
    val alt: String,
)
