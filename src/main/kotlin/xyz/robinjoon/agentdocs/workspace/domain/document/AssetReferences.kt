package xyz.robinjoon.agentdocs.workspace.domain.document

import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetMediaType

// Current and historical callers supply their own blocks; this function never loads or changes assets.
object AssetReferences {
    fun of(blocks: List<Block>): Set<AssetId> = blocks.flatMap { of(it) }.toSet()

    // The stored type each drawing reference must have. One id cannot be both a drawing's SVG and another drawing's
    // scene, because the stored file has exactly one type and the write would then pass a check it cannot satisfy.
    fun expectedTypes(blocks: List<Block>): Map<AssetId, AssetMediaType> {
        val expected = LinkedHashMap<AssetId, AssetMediaType>()
        blocks.filterIsInstance<DrawingBlock>().forEach { drawing ->
            expect(expected, drawing.svg, AssetMediaType.SVG)
            expect(expected, drawing.scene, AssetMediaType.EXCALIDRAW)
        }
        return expected.toMap()
    }

    private fun expect(
        expected: MutableMap<AssetId, AssetMediaType>,
        id: AssetId,
        type: AssetMediaType,
    ) {
        val previous = expected.putIfAbsent(id, type)
        if (previous != null && previous != type) {
            throw InvalidDocumentException("asset $id cannot be both a drawing's SVG and a drawing's scene")
        }
    }

    private fun of(block: Block): Set<AssetId> =
        when (block) {
            is ImageBlock -> setOf(block.asset)
            is DrawingBlock -> setOf(block.svg, block.scene)
            is Paragraph -> InlineMarkdown.assetIds(block.text.markdown)
            is Heading -> InlineMarkdown.assetIds(block.text.markdown)
            is ListItem -> InlineMarkdown.assetIds(block.text.markdown)
            is Quote -> InlineMarkdown.assetIds(block.text.markdown)
            is Callout -> InlineMarkdown.assetIds(block.text.markdown)
            is TableBlock -> block.rows.flatMap { row -> row.cells.flatMap { InlineMarkdown.assetIds(it.markdown) } }.toSet()
            is CodeBlock, is Divider -> emptySet()
        }
}
