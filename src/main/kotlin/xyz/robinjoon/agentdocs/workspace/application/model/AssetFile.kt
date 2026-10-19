package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.asset.Asset

class AssetFile(
    val asset: Asset,
    val bytes: ByteArray,
)
