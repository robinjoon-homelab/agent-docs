package xyz.robinjoon.agentdocs.workspace.domain.thread

import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowElement
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowScreen
import xyz.robinjoon.agentdocs.workspace.domain.document.InlineMarkdown
import xyz.robinjoon.agentdocs.workspace.domain.document.InlineText
import xyz.robinjoon.agentdocs.workspace.domain.document.TableBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.TableRow
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.BlockConflict
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.EditConflictException
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.MarkdownReader

object SuggestionPatch {
    fun apply(
        blocks: List<Block>,
        basis: SuggestionBasis,
    ): List<Block> {
        val id = blockId(basis.anchor)
        val block = blocks.firstOrNull { it.id == id }
        if (block == null || BlockMarkdown.hash(block) != basis.hash) conflict(block, id)
        val changed =
            when (val anchor = basis.anchor) {
                is Anchor.Whole -> whole(block, basis)
                is Anchor.Cell -> cell(block, anchor, basis)
                is Anchor.Text -> text(block, anchor, basis)
                Anchor.Document -> throw InvalidThreadException("document-wide suggestions are not supported")
            }
        return blocks.map { if (it.id == id) changed else it }
    }

    fun blockId(anchor: Anchor): BlockId =
        when (anchor) {
            is Anchor.Whole -> anchor.blockId
            is Anchor.Cell -> anchor.blockId
            is Anchor.Text -> anchor.blockId
            Anchor.Document -> throw InvalidThreadException("a suggestion needs a range")
        }

    private fun whole(
        block: Block,
        basis: SuggestionBasis,
    ): Block {
        flowDescription(block, basis)?.let { return it }
        if (BlockMarkdown.of(block) != basis.before) conflict(block, block.id)
        val parsed =
            MarkdownReader.readBlocks(basis.after).singleOrNull()?.block
                ?: throw InvalidThreadException("a block suggestion must produce exactly one block")
        if (block is TableBlock && parsed !is TableBlock) {
            throw InvalidThreadException("a table suggestion must produce a table")
        }
        return if (block is TableBlock && parsed is TableBlock) {
            parsed.withId(block.id).let { (it as TableBlock).keepingIdsOf(block) }
        } else {
            parsed.withId(block.id)
        }
    }

    private fun flowDescription(
        block: Block,
        basis: SuggestionBasis,
    ): Block? {
        if (block is FlowScreen) {
            if (block.desc != basis.before) conflict(block, block.id)
            return block.copy(desc = basis.after)
        }
        if (block is FlowElement) {
            if (block.text.desc != basis.before) conflict(block, block.id)
            return block.copy(text = block.text.copy(desc = basis.after))
        }
        return null
    }

    private fun cell(
        block: Block,
        anchor: Anchor.Cell,
        basis: SuggestionBasis,
    ): Block {
        val table = block as? TableBlock ?: conflict(block, block.id)
        val column = table.columnIndex(anchor.columnId)
        val row = table.row(anchor.rowId) ?: conflict(block, block.id)
        if (column < 0 || row.cells[column].markdown != basis.before || row.cells[column].plainText() != anchor.quote) {
            conflict(block, block.id)
        }
        val cells = row.cells.mapIndexed { index, value -> if (index == column) InlineText(basis.after) else value }
        return TableBlock.of(table.id, table.columns, table.rows.map { if (it.id == row.id) TableRow.of(row.id, cells) else it })
    }

    private fun text(
        block: Block,
        anchor: Anchor.Text,
        basis: SuggestionBasis,
    ): Block {
        if (InlineMarkdown.plainText(basis.before) != anchor.quote) conflict(block, block.id)
        val plain = block.plainText()
        if (plain.substringOrNull(anchor.start, anchor.end) != anchor.quote) conflict(block, block.id)
        val markdown = BlockMarkdown.of(block)
        val positions = markdown.indices.filter { markdown.startsWith(basis.before, it) }
        val replacements = positions.map { markdown.replaceRange(it, it + basis.before.length, basis.after) }
        val expected = plain.replaceRange(anchor.start, anchor.end, InlineMarkdown.plainText(basis.after))
        val parsed =
            replacements
                .mapNotNull { MarkdownReader.readBlocks(it).singleOrNull()?.block }
                .filter { it.plainText() == expected }
        val changed = parsed.singleOrNull() ?: conflict(block, block.id)
        return changed.withId(block.id)
    }

    private fun String.substringOrNull(
        start: Int,
        end: Int,
    ): String? = if (start >= 0 && end <= length) substring(start, end) else null

    private fun conflict(
        block: Block?,
        id: BlockId,
    ): Nothing = throw EditConflictException(listOf(BlockConflict(id, block?.let(BlockMarkdown::hash), block?.let(BlockMarkdown::of))))
}
