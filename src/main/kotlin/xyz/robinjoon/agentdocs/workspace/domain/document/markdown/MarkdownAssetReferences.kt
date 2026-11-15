package xyz.robinjoon.agentdocs.workspace.domain.document.markdown

import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.document.AssetReferences

object MarkdownAssetReferences {
    fun of(markdown: String): Set<AssetId> = AssetReferences.of(MarkdownReader.readBlocks(markdown).map { it.block })
}
