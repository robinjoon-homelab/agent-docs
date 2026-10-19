package xyz.robinjoon.agentdocs.workspace.domain.document.history

import xyz.robinjoon.agentdocs.workspace.domain.document.Block

// Compares two block lists by block id. A block that only moved is not a change. Removed blocks are listed where
// they used to be, right after the surviving block that preceded them.
object BlockDiff {
    fun between(
        before: List<Block>,
        after: List<Block>,
    ): List<BlockChange> {
        val oldById = before.associateBy { it.id }
        val newPositions = after.withIndex().associate { (index, block) -> block.id to index.toDouble() }
        val present =
            after.mapNotNull { block ->
                val old = oldById[block.id]
                val kind = if (old == null) BlockChangeKind.ADDED else BlockChangeKind.MODIFIED
                if (old == block) null else newPositions.getValue(block.id) to BlockChange(block.id, kind, old, block)
            }
        val removed =
            before.withIndex().filter { it.value.id !in newPositions }.map { (index, block) ->
                val anchor = before.subList(0, index).lastOrNull { it.id in newPositions }
                val position = (anchor?.let { newPositions.getValue(it.id) } ?: -1.0) + REMOVED_OFFSET
                position to BlockChange(block.id, BlockChangeKind.REMOVED, block, null)
            }
        return (present + removed).sortedBy { it.first }.map { it.second }
    }

    // Whether the blocks both lists keep stand in another order. A move is no change to the block, but it is one to
    // the document.
    fun reordered(
        before: List<Block>,
        after: List<Block>,
    ): Boolean {
        val kept = before.map { it.id }.toSet() intersect after.map { it.id }.toSet()
        return before.map { it.id }.filter { it in kept } != after.map { it.id }.filter { it in kept }
    }

    private const val REMOVED_OFFSET = 0.5
}
