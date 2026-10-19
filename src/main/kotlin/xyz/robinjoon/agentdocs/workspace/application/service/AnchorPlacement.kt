package xyz.robinjoon.agentdocs.workspace.application.service

import xyz.robinjoon.agentdocs.workspace.application.model.AnchorRequest
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.Divider
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.ImageBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.TableBlock
import xyz.robinjoon.agentdocs.workspace.domain.thread.Anchor

// Turns what a client says about a new anchor into an anchor checked against the current document.
internal object AnchorPlacement {
    fun place(
        request: AnchorRequest,
        document: Document,
    ): Anchor =
        when (request) {
            is AnchorRequest.Text -> text(request, document.block(request.blockId) ?: missing(request.blockId))
            is AnchorRequest.Cell -> cell(request, document.block(request.blockId) ?: missing(request.blockId))
            is AnchorRequest.Whole -> Anchor.Whole((document.block(request.blockId) ?: missing(request.blockId)).id)
            AnchorRequest.Document -> Anchor.Document
        }

    private fun text(
        request: AnchorRequest.Text,
        block: Block,
    ): Anchor.Text {
        if (block is TableBlock || block is ImageBlock || block is Divider) {
            throw InvalidRequestException("block ${block.id} has no text range", "anchor on a table cell or on the whole block")
        }
        val text = block.plainText()
        val start = request.start ?: onlyOccurrence(text, request.quote, block.id)
        if (request.quote.isEmpty() || start < 0 || !text.startsWith(request.quote, start)) {
            throw InvalidRequestException(
                "the quote is not at offset $start of block ${block.id}",
                "copy the quote exactly from the block's plain text",
            )
        }
        return Anchor.Text(block.id, start, start + request.quote.length, request.quote)
    }

    private fun onlyOccurrence(
        text: String,
        quote: String,
        block: BlockId,
    ): Int {
        if (quote.isEmpty()) return -1
        val positions =
            generateSequence(text.indexOf(quote).takeIf { it >= 0 }) {
                text.indexOf(quote, it + 1).takeIf { next ->
                    next >= 0
                }
            }.toList()
        return positions.singleOrNull() ?: throw InvalidRequestException(
            if (positions.isEmpty()) "the quote is not in block $block" else "the quote occurs ${positions.size} times in block $block",
            "pass start, the offset of the quote in the block's plain text",
        )
    }

    private fun cell(
        request: AnchorRequest.Cell,
        block: Block,
    ): Anchor.Cell {
        val table = block as? TableBlock ?: throw InvalidRequestException("block ${block.id} is not a table", "use a text or block anchor")
        val row = table.row(request.rowId) ?: throw InvalidRequestException("row ${request.rowId} is not in table ${table.id}")
        val column = table.columnIndex(request.columnId)
        if (column < 0) throw InvalidRequestException("column ${request.columnId} is not in table ${table.id}")
        return Anchor.Cell(table.id, row.id, request.columnId, row.cells[column].plainText())
    }

    private fun missing(id: BlockId): Nothing =
        throw InvalidRequestException("block $id is not in the document", "read the document again for current block ids")
}
