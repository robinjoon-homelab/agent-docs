package xyz.robinjoon.agentdocs.workspace.domain.document.history

import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowDesignBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowElement
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowSettings
import xyz.robinjoon.agentdocs.workspace.domain.document.InvalidDocumentException
import xyz.robinjoon.agentdocs.workspace.domain.document.LegacyFlowUpgrade

object BlockRestoration {
    fun restore(
        current: List<Block>,
        earlier: List<Block>,
        id: BlockId,
    ): List<Block> {
        requireUnique(earlier)
        requireUnique(current)
        val original = earlier.singleOrNull { it.id == id } ?: throw InvalidDocumentException("the revision does not contain block $id")
        if (current.firstOrNull() is FlowDesignBlock && earlier.firstOrNull() is FlowSettings) {
            return restoreLegacy(current, earlier, original)
        }
        if (original is FlowDesignBlock) requireDesignParents(current, earlier, original)
        val existing = current.indexOfFirst { it.id == id }
        if (existing >= 0) return current.map { if (it.id == id) original else it }
        val at =
            if (earlier.firstOrNull() is FlowSettings) {
                FlowRestoration.insertion(current, earlier, original)
            } else {
                insertionPoint(current, earlier, id)
            }
        return current.take(at) + original + current.drop(at)
    }

    private fun restoreLegacy(
        current: List<Block>,
        earlier: List<Block>,
        original: Block,
    ): List<Block> {
        val projected = LegacyFlowUpgrade.convert(earlier)
        val updated = if (original is FlowElement) LegacyElementRestoration.restore(current, projected, original) else current
        return restore(updated, projected, original.id)
    }

    private fun requireUnique(blocks: List<Block>) {
        if (blocks.map { it.id }.toSet().size !=
            blocks.size
        ) {
            throw InvalidDocumentException("block restoration found conflicting block ids")
        }
    }

    private fun requireDesignParents(
        current: List<Block>,
        earlier: List<Block>,
        original: FlowDesignBlock,
    ) {
        if (current.firstOrNull() !is FlowDesignBlock) throw InvalidDocumentException("restore a design in a v4 flow")
        val old = earlier.filterIsInstance<FlowDesignBlock>()
        val parents = listOf("screen" to original.string("screen"), "state" to original.string("state"))
        for ((kind, key) in parents.filter { it.second != null }) {
            val parent = old.singleOrNull { it.kind == kind && it.designId == key }
            if (parent == null || current.none { it is FlowDesignBlock && it.id == parent.id && it.designId == key && it.kind == kind }) {
                throw InvalidDocumentException("restore the design block's $kind first")
            }
        }
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
