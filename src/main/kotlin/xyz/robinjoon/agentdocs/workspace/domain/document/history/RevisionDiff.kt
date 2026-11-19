package xyz.robinjoon.agentdocs.workspace.domain.document.history

import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown
import java.util.Collections

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
            val changedIds = changes.map { it.blockId }.toSet()
            val older = PatchSource(before)
            val newer = PatchSource(after)
            val patch =
                buildString {
                    append("--- rev $from\n+++ rev $to\n")
                    while (older.hasNext() || newer.hasNext()) {
                        appendHunk(older.nextChange(changedIds), newer.nextChange(changedIds))
                    }
                }
            return RevisionDiff(from, to, changes, patch)
        }

        private fun StringBuilder.appendHunk(
            old: PatchRange,
            new: PatchRange,
        ) {
            val ids = (old.ids + new.ids).distinct()
            if (ids.isEmpty()) return
            // Adjacent paragraphs share a blank separator even when both paragraphs are replaced.
            val skip = if (old.lines.firstOrNull() == "" && new.lines.firstOrNull() == "") 1 else 0
            val drop = if (old.endsWithBlankAfter(skip) && new.endsWithBlankAfter(skip)) 1 else 0
            append("@@ -${old.coordinates(skip, drop)} +${new.coordinates(skip, drop)} @@")
            ids.forEach { append(" b:$it") }
            append('\n')
            old.lines.subList(skip, old.lines.size - drop).forEach { append("-$it\n") }
            new.lines.subList(skip, new.lines.size - drop).forEach { append("+$it\n") }
        }
    }

    private class PatchRange(
        val offset: Int,
        val lines: List<String>,
        val ids: List<BlockId>,
    ) {
        fun endsWithBlankAfter(skip: Int): Boolean = lines.size > skip && lines.last().isEmpty()

        // A zero-length range starts at the number of preceding lines; all other ranges start at the first changed line.
        fun coordinates(
            skip: Int,
            drop: Int,
        ): String {
            val count = lines.size - skip - drop
            val start = offset + skip + if (count == 0) 0 else 1
            return "$start,$count"
        }
    }

    // Blocks outside BlockDiff's changes have identical contents and occur in the same order on both sides.
    // Use them as anchors. Their leading separator can change independently, so it belongs to the preceding hunk.
    private class PatchSource(
        private val blocks: List<Block>,
    ) {
        private val parts = BlockMarkdown.parts(blocks, annotated = false).iterator()
        private var index = 0
        private var line = 0

        fun hasNext(): Boolean = index < blocks.size

        fun nextChange(changedIds: Set<BlockId>): PatchRange {
            val start = line
            val ids = ArrayList<BlockId>()
            val lines = ArrayList<String>()
            while (hasNext() && blocks[index].id in changedIds) {
                ids.add(blocks[index++].id)
                lines.addAll(parts.next().removePrefix("\n").split('\n'))
            }
            if (hasNext()) {
                val anchor = parts.next().removePrefix("\n")
                if (anchor.startsWith('\n')) lines.add("")
                line += anchor.removePrefix("\n").count { it == '\n' } + 1
                index++
            }
            line += lines.size
            return PatchRange(start, lines, ids)
        }
    }
}
