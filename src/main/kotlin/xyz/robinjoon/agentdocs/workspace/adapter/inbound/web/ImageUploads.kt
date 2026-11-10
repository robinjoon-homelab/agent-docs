package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import xyz.robinjoon.agentdocs.workspace.domain.asset.Asset

// What an agent gets back for an uploaded image, the same from docs_upload and from an upload command.
internal object ImageUploads {
    fun resultOf(
        asset: Asset,
        alt: String,
    ): Map<String, Any?> =
        mapOf(
            "assetId" to asset.id.value,
            "mediaType" to asset.content.mediaType.mime,
            "markdown" to "![${alt.replace("]", "\\]")}](asset:${asset.id})",
        )
}
