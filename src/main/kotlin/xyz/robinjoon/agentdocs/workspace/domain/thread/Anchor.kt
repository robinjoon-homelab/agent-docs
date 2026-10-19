package xyz.robinjoon.agentdocs.workspace.domain.thread

import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId

// What a thread is attached to. The quote keeps what the thread was about even after the text is gone.
sealed interface Anchor {
    // A character range in the block's plain text.
    data class Text(
        val blockId: BlockId,
        val start: Int,
        val end: Int,
        val quote: String,
    ) : Anchor {
        init {
            if (start < 0 || end <= start || end - start != quote.length || quote.length > MAX_QUOTE_LENGTH) {
                throw InvalidThreadException("a text anchor covers 1 to $MAX_QUOTE_LENGTH characters and its quote is exactly that text")
            }
        }
    }

    // A table cell, addressed by row and column ids so it follows the cell when rows or columns move.
    data class Cell(
        val blockId: BlockId,
        val rowId: BlockId,
        val columnId: BlockId,
        val quote: String,
    ) : Anchor

    data class Whole(
        val blockId: BlockId,
    ) : Anchor

    data object Document : Anchor

    companion object {
        const val MAX_QUOTE_LENGTH = 2_000
    }
}
