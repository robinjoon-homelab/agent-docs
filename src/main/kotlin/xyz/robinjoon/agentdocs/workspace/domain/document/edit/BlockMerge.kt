package xyz.robinjoon.agentdocs.workspace.domain.document.edit

import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown

// Merges a whole-document save with changes others made since the writer's base revision, block by block. A block
// changed on only one side takes that side; a block changed on both sides, or deleted on one side and changed on the
// other, is a conflict. Blocks others added are kept after the block that precedes them now.
object BlockMerge {
    fun merge(
        base: List<Block>,
        current: List<Block>,
        submitted: List<Block>,
    ): MergeResult {
        val baseById = base.associateBy { it.id }
        val currentById = current.associateBy { it.id }
        val submittedIds = submitted.map { it.id }.toSet()
        val conflicts = ArrayList<BlockConflict>()
        val kept = submitted.mapNotNull { resolve(it, baseById[it.id], currentById[it.id], conflicts) }
        current
            .filter { it.id !in submittedIds && baseById[it.id] != null && baseById[it.id] != it }
            .forEach { conflicts += conflictOf(it.id, it) }
        val addedByOthers = current.filter { it.id !in baseById && it.id !in submittedIds }
        return MergeResult(withOthersAdditions(kept, current, addedByOthers.map { it.id }.toSet()), conflicts)
    }

    private fun resolve(
        mine: Block,
        base: Block?,
        theirs: Block?,
        conflicts: MutableList<BlockConflict>,
    ): Block? {
        val changedByMe = mine != base
        val changedByThem = theirs != base
        if (changedByMe && changedByThem && mine != theirs) {
            conflicts += conflictOf(mine.id, theirs)
            return theirs
        }
        return when {
            theirs == null && base != null -> null
            changedByMe -> mine
            else -> theirs ?: mine
        }
    }

    private fun conflictOf(
        id: BlockId,
        current: Block?,
    ): BlockConflict = BlockConflict(id, current?.let(BlockMarkdown::hash), current?.let(BlockMarkdown::of))

    private fun withOthersAdditions(
        kept: List<Block>,
        current: List<Block>,
        added: Set<BlockId>,
    ): List<Block> {
        val result = ArrayList(kept)
        current.forEachIndexed { position, block ->
            if (block.id in added) {
                val previous = current.subList(0, position).lastOrNull { candidate -> result.any { it.id == candidate.id } }
                val at = previous?.let { prior -> result.indexOfFirst { it.id == prior.id } + 1 } ?: 0
                result.add(at, block)
            }
        }
        return result
    }
}
