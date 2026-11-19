package xyz.robinjoon.agentdocs.workspace.domain.document.history

import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId

// Compares block contents and the relative order of surviving block IDs. Removed blocks stay next to their old predecessor.
object BlockDiff {
    fun between(
        before: List<Block>,
        after: List<Block>,
    ): List<BlockChange> {
        val oldById = before.associateBy { it.id }
        val newPositions = after.withIndex().associate { (index, block) -> block.id to index.toDouble() }
        val moved = movedIds(before, after)
        val present =
            after.mapNotNull { block ->
                val old = oldById[block.id]
                val kind = kindOf(old, block, moved) ?: return@mapNotNull null
                newPositions.getValue(block.id) to BlockChange(block.id, kind, old, block)
            }
        val removed =
            before.withIndex().filter { it.value.id !in newPositions }.map { (index, block) ->
                val anchor = before.subList(0, index).lastOrNull { it.id in newPositions }
                val position = (anchor?.let { newPositions.getValue(it.id) } ?: -1.0) + REMOVED_OFFSET
                position to BlockChange(block.id, BlockChangeKind.REMOVED, block, null)
            }
        return (present + removed).sortedBy { it.first }.map { it.second }
    }

    fun reordered(
        before: List<Block>,
        after: List<Block>,
    ): Boolean = movedIds(before, after).isNotEmpty()

    private fun kindOf(
        old: Block?,
        block: Block,
        moved: Set<BlockId>,
    ): BlockChangeKind? =
        when {
            old == null -> BlockChangeKind.ADDED
            old != block -> BlockChangeKind.MODIFIED
            block.id in moved -> BlockChangeKind.MOVED
            else -> null
        }

    private fun movedIds(
        before: List<Block>,
        after: List<Block>,
    ): Set<BlockId> {
        val older = before.map { it.id }
        val newer = after.map { it.id }
        val kept = older.toSet() intersect newer.toSet()
        val oldOrder = older.filter { it in kept }
        val newOrder = newer.filter { it in kept }
        return newOrder.filterIndexed { index, id -> id != oldOrder[index] }.toSet()
    }

    private const val REMOVED_OFFSET = 0.5
}
