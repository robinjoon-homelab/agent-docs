package xyz.robinjoon.agentdocs.workspace.domain.thread

import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.TableBlock
import kotlin.math.abs

// Finds an anchor in the current blocks when the thread is read, so edits never have to rewrite threads. A text range
// stays where it was if the same text is still there and otherwise moves to the closest occurrence of its quote in the
// same block. When the quote itself was edited, the range follows the edit from anchoredText, the block's text when
// the anchor was placed; it detaches only when every quoted character is gone or the block was deleted.
object AnchorResolver {
    fun resolve(
        anchor: Anchor,
        blocks: List<Block>,
        anchoredText: String? = null,
    ): AnchorResolution =
        when (anchor) {
            is Anchor.Text -> {
                text(anchor, blocks.firstOrNull { it.id == anchor.blockId }, anchoredText)
            }

            is Anchor.Cell -> {
                cell(anchor, blocks.firstOrNull { it.id == anchor.blockId } as? TableBlock)
            }

            is Anchor.Whole -> {
                if (blocks.any { it.id == anchor.blockId }) {
                    AnchorResolution.Attached(
                        null,
                        null,
                    )
                } else {
                    AnchorResolution.Detached
                }
            }

            Anchor.Document -> {
                AnchorResolution.Attached(null, null)
            }
        }

    private fun text(
        anchor: Anchor.Text,
        block: Block?,
        anchoredText: String?,
    ): AnchorResolution {
        val text = block?.plainText() ?: return AnchorResolution.Detached
        if (anchor.end <= text.length && text.substring(anchor.start, anchor.end) == anchor.quote) {
            return AnchorResolution.Attached(anchor.start, anchor.end)
        }
        val nearest =
            generateSequence(text.indexOf(anchor.quote)) { previous -> text.indexOf(anchor.quote, previous + 1).takeIf { it >= 0 } }
                .takeWhile { it >= 0 }
                .minByOrNull { abs(it - anchor.start) }
        return nearest?.let { AnchorResolution.Attached(it, it + anchor.quote.length) }
            ?: anchoredText?.let { followEdit(anchor, it, text) }
            ?: AnchorResolution.Detached
    }

    // The edits between the two texts are taken as one changed region between their common start and common end. The
    // common parts stop at the quote's edges, so the region covers the quote: "delay" deleted from "Retry delay doubles"
    // is not mistaken for "elay d". A range end inside the region moves to its edge, so a rewritten quote covers the
    // new words.
    private fun followEdit(
        anchor: Anchor.Text,
        before: String,
        after: String,
    ): AnchorResolution {
        if (anchor.end > before.length || before.substring(anchor.start, anchor.end) != anchor.quote) return AnchorResolution.Detached
        val prefix = minOf(before.commonPrefixWith(after).length, anchor.start)
        val suffix = minOf(before.substring(prefix).commonSuffixWith(after.substring(prefix)).length, before.length - anchor.end)
        val edit = Edit(prefix, before.length - suffix, after.length - suffix)
        val start = edit.map(anchor.start, edit.prefix)
        val end = edit.map(anchor.end, edit.insertedEnd)
        return if (end > start) AnchorResolution.Attached(start, end) else AnchorResolution.Detached
    }

    // before[prefix, removedEnd) became after[prefix, insertedEnd).
    private class Edit(
        val prefix: Int,
        val removedEnd: Int,
        val insertedEnd: Int,
    ) {
        fun map(
            position: Int,
            inside: Int,
        ): Int =
            when {
                position <= prefix -> position
                position >= removedEnd -> position + insertedEnd - removedEnd
                else -> inside
            }
    }

    private fun cell(
        anchor: Anchor.Cell,
        table: TableBlock?,
    ): AnchorResolution {
        val present = table != null && table.row(anchor.rowId) != null && table.columnIndex(anchor.columnId) >= 0
        return if (present) AnchorResolution.Attached(null, null) else AnchorResolution.Detached
    }
}
