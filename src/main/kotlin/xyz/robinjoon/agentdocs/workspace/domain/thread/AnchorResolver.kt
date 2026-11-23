package xyz.robinjoon.agentdocs.workspace.domain.thread

import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowDesignBlock
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
                whole(anchor, blocks)
            }

            Anchor.Document -> {
                AnchorResolution.Attached(null, null)
            }
        }

    private fun whole(
        anchor: Anchor.Whole,
        blocks: List<Block>,
    ): AnchorResolution {
        val block = blocks.find { it.id == anchor.blockId } ?: return AnchorResolution.Detached
        val stateId = anchor.viewedStateId ?: return AnchorResolution.Attached(null, null)
        val state = blocks.find { it.id == stateId } as? FlowDesignBlock ?: return AnchorResolution.Detached
        if (block !is FlowDesignBlock || block.kind != "annotation" || state.kind != "state") return AnchorResolution.Detached
        if (block.string("screen") != state.string("screen")) return AnchorResolution.Detached
        val own = block.string("state")
        return if (own == null || own == state.designId) AnchorResolution.Attached(null, null) else AnchorResolution.Detached
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

    // Match the text outside each end separately. Treating all changes as one replacement would attach a deleted
    // quote to the edits before and after it. Offsets stay in the same UTF-16 units used when the anchor was placed.
    private fun followEdit(
        anchor: Anchor.Text,
        before: String,
        after: String,
    ): AnchorResolution {
        if (anchor.end > before.length || !before.startsWith(anchor.quote, anchor.start)) return AnchorResolution.Detached
        val start = boundary(before.substring(0, anchor.start), anchor.quote, after) ?: return AnchorResolution.Detached
        val right = before.substring(anchor.end).reversed()
        val fromEnd = boundary(right, anchor.quote.reversed(), after.reversed()) ?: return AnchorResolution.Detached
        val end = after.length - fromEnd
        return if (start < end) AnchorResolution.Attached(start, end) else AnchorResolution.Detached
    }

    // An incidental match outside the quote must not consume a better match inside it.
    private fun boundary(
        outside: String,
        quote: String,
        current: String,
    ): Int? {
        if (outside.isEmpty()) return 0
        if (current.startsWith(outside) && outside.length >= minOf(quote.length, CONTEXT_LENGTH)) return outside.length
        val adjoining = contextEnd(outside, current, outside.length)
        // Trimming a long quote may leave only its middle. Locate the surviving characters before choosing the window.
        val present = current.toSet()
        val surviving = quote.filter { it in present }
        val inside = contextEnd(surviving.reversed(), current.reversed(), current.length - outside.length) ?: return adjoining?.end
        val start = current.length - inside.end
        if (adjoining == null || (adjoining.end > start && inside.score > adjoining.score)) return start
        return adjoining.end
    }

    // The closest best match for the adjacent context wins. A free start allows edits farther from the quote;
    // bounding the pattern keeps alignment linear in the block length even for unrelated 20,000-character blocks.
    private fun contextEnd(
        original: String,
        current: String,
        expectedEnd: Int,
    ): ContextMatch? {
        if (original.isEmpty()) return null
        val context = original.takeLast(CONTEXT_LENGTH)
        val distances = IntArray(context.length + 1) { it }
        var bestCost = context.length
        var bestEnd = expectedEnd
        for (index in current.indices) {
            val cost = advanceDistances(context, current[index], distances)
            val end = index + 1
            if (cost < bestCost || (cost == bestCost && abs(end - expectedEnd) < abs(bestEnd - expectedEnd))) {
                bestCost = cost
                bestEnd = end
            }
        }
        return if (bestCost < context.length) ContextMatch(bestEnd, context.length - bestCost) else null
    }

    private data class ContextMatch(
        val end: Int,
        val score: Int,
    )

    // One column of substring edit distance. The empty pattern costs zero at every possible start.
    private fun advanceDistances(
        context: String,
        next: Char,
        distances: IntArray,
    ): Int {
        var diagonal = 0
        for (index in context.indices) {
            val above = distances[index + 1]
            val replace = diagonal + if (context[index] == next) 0 else 1
            distances[index + 1] = minOf(replace, above + 1, distances[index] + 1)
            diagonal = above
        }
        return distances.last()
    }

    private const val CONTEXT_LENGTH = 32

    private fun cell(
        anchor: Anchor.Cell,
        table: TableBlock?,
    ): AnchorResolution {
        val present = table != null && table.row(anchor.rowId) != null && table.columnIndex(anchor.columnId) >= 0
        return if (present) AnchorResolution.Attached(null, null) else AnchorResolution.Detached
    }
}
