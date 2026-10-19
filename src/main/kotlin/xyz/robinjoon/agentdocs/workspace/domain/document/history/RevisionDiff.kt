package xyz.robinjoon.agentdocs.workspace.domain.document.history

import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.ListItem
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown
import java.util.Collections

// The block changes between two revisions and the same changes as a unified diff of the plain markdown. Each hunk
// covers one block and names it, so an agent can go from a hunk to the block id it edits.
class RevisionDiff private constructor(
    val from: Int,
    val to: Int,
    changes: List<BlockChange>,
    val unified: String,
) {
    val changes: List<BlockChange> = Collections.unmodifiableList(changes.toList())

    val changedBlocks: List<BlockId> get() = changes.map { it.blockId }

    companion object {
        fun between(
            from: Int,
            before: List<Block>,
            to: Int,
            after: List<Block>,
        ): RevisionDiff {
            val changes = BlockDiff.between(before, after)
            val oldLines = LineLayout.of(before)
            val newLines = LineLayout.of(after)
            val hunks = changes.joinToString("") { hunk(it, oldLines, newLines) }
            return RevisionDiff(from, to, changes, "--- rev $from\n+++ rev $to\n$hunks")
        }

        private fun hunk(
            change: BlockChange,
            oldLines: LineLayout,
            newLines: LineLayout,
        ): String {
            val removed =
                change.before
                    ?.let(BlockMarkdown::of)
                    ?.split('\n')
                    .orEmpty()
            val added =
                change.after
                    ?.let(BlockMarkdown::of)
                    ?.split('\n')
                    .orEmpty()
            val oldStart = oldLines.startOf(change.blockId) ?: oldLines.insertionPoint(change, newLines)
            val newStart = newLines.startOf(change.blockId) ?: newLines.insertionPoint(change, oldLines)
            val header = "@@ -$oldStart,${removed.size} +$newStart,${added.size} @@ b:${change.blockId}\n"
            return header + removed.joinToString("") { "-$it\n" } + added.joinToString("") { "+$it\n" }
        }
    }

    // The line each block starts on in the written plain markdown, counting from 1.
    private class LineLayout(
        private val blocks: List<Block>,
    ) {
        private val starts = HashMap<BlockId, Int>()

        init {
            var line = 1
            blocks.forEachIndexed { index, block ->
                line += blankLinesBefore(index)
                starts[block.id] = line
                line += BlockMarkdown.of(block).count { it == '\n' } + 1
            }
        }

        // The writer keeps adjacent list items on adjacent lines and puts one blank line between other blocks.
        private fun blankLinesBefore(index: Int): Int =
            when {
                index == 0 -> 0
                blocks[index] is ListItem && blocks[index - 1] is ListItem -> 0
                else -> 1
            }

        fun startOf(id: BlockId): Int? = starts[id]

        // For a block missing on this side, the line after the nearest block that both sides share, found by walking
        // back through the other side's order.
        fun insertionPoint(
            change: BlockChange,
            other: LineLayout,
        ): Int {
            val order = other.blocks.map { it.id }
            val shared = order.subList(0, order.indexOf(change.blockId).coerceAtLeast(0)).lastOrNull { it in starts }
            return shared?.let { id -> starts.getValue(id) + BlockMarkdown.of(blocks.first { it.id == id }).count { it == '\n' } + 1 } ?: 0
        }

        companion object {
            fun of(blocks: List<Block>): LineLayout = LineLayout(blocks)
        }
    }
}
