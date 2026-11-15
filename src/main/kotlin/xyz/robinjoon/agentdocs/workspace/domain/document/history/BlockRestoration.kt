package xyz.robinjoon.agentdocs.workspace.domain.document.history

import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.InvalidDocumentException

object BlockRestoration {
    fun restore(
        current: List<Block>,
        earlier: List<Block>,
        id: BlockId,
    ): List<Block> {
        if (earlier.map { it.id }.distinct().size != earlier.size || current.map { it.id }.distinct().size != current.size) {
            throw InvalidDocumentException("block restoration found conflicting block ids")
        }
        val original = earlier.singleOrNull { it.id == id } ?: throw InvalidDocumentException("the revision does not contain block $id")
        val existing = current.indexOfFirst { it.id == id }
        if (existing >= 0) return current.map { if (it.id == id) original else it }
        val at = insertionPoint(current, earlier, id)
        return current.take(at) + original + current.drop(at)
    }

    private fun insertionPoint(
        current: List<Block>,
        earlier: List<Block>,
        id: BlockId,
    ): Int {
        val at = earlier.indexOfFirst { it.id == id }
        val positions = current.mapIndexed { index, block -> block.id to index }.toMap()
        val before = earlier.take(at).asReversed().firstNotNullOfOrNull { positions[it.id] }
        if (before != null) return before + 1
        return earlier.drop(at + 1).firstNotNullOfOrNull { positions[it.id] } ?: current.size
    }
}
