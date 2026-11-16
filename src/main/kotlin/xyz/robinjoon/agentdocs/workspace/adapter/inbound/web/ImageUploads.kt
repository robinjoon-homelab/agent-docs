package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import xyz.robinjoon.agentdocs.workspace.domain.asset.Asset
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetMediaType

// All clients use the same asset references; HTML replacement never mutates old bytes.
internal object ImageUploads {
    // Files never run script or load anything; data: fonts let a drawing's inlined handwriting font show when opened.
    const val ATTACHMENT_POLICY = "sandbox; default-src 'none'; img-src data:; style-src 'unsafe-inline'; font-src data:"

    fun resultOf(
        asset: Asset,
        alt: String,
    ): Map<String, Any?> =
        mapOf(
            "assetId" to asset.id.value,
            "mediaType" to asset.content.mediaType.mime,
            "markdown" to markdown(asset, if (asset.content.mediaType.isImage) alt else alt.ifBlank { asset.origin.fileName }),
        )

    private fun markdown(
        asset: Asset,
        label: String,
    ): String {
        val text = label.replace("\\", "\\\\").replace("]", "\\]")
        val target = "asset:${asset.id}"
        return when {
            asset.content.mediaType == AssetMediaType.HTML -> "![$text]($target \"mockup\")"
            asset.content.mediaType.isImage -> "![$text]($target)"
            else -> "[$text]($target)"
        }
    }
}
