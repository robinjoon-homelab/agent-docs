package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.asset.Asset

// The UTF-8 text of an HTML, SVG, or drawing scene attachment as stored. A scene also carries what it contains.
class AssetSource(
    val asset: Asset,
    val text: String,
    val scene: VerifiedScene?,
)
