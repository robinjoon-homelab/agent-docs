package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetMediaType

// An image whose bytes match its type. For SVG the bytes are the cleaned document without scripts.
class VerifiedImage(
    val mediaType: AssetMediaType,
    val bytes: ByteArray,
)
