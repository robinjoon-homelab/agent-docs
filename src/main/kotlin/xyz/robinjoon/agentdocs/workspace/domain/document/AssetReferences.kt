package xyz.robinjoon.agentdocs.workspace.domain.document

import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId

// Current and historical callers supply their own blocks; this function never loads or changes assets.
object AssetReferences {
    fun of(blocks: List<Block>): Set<AssetId> = blocks.flatMap { of(it) }.toSet()

    private fun of(block: Block): Set<AssetId> =
        when (block) {
            is ImageBlock -> setOf(block.asset)
            is Paragraph -> InlineMarkdown.assetIds(block.text.markdown)
            is Heading -> InlineMarkdown.assetIds(block.text.markdown)
            is ListItem -> InlineMarkdown.assetIds(block.text.markdown)
            is Quote -> InlineMarkdown.assetIds(block.text.markdown)
            is Callout -> InlineMarkdown.assetIds(block.text.markdown)
            is TableBlock -> block.rows.flatMap { row -> row.cells.flatMap { InlineMarkdown.assetIds(it.markdown) } }.toSet()
            else -> emptySet()
        }
}
