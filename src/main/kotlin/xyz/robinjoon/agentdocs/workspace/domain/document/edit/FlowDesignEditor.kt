package xyz.robinjoon.agentdocs.workspace.domain.document.edit

import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowDesignBlock

internal object FlowDesignEditor {
    fun apply(
        blocks: List<Block>,
        operation: EditOperation,
        index: Int,
    ): List<Block> =
        when (operation) {
            is EditOperation.Delete -> blocks - family(blocks, operation.target).toSet()
            is EditOperation.Move -> move(blocks, operation)
            is EditOperation.Insert -> insert(blocks, operation, index)
            is EditOperation.Replace -> replace(blocks, operation, index)
            else -> DocumentEditor.applyOrdinary(blocks, operation, index)
        }

    private fun family(
        blocks: List<Block>,
        target: BlockId,
    ): List<Block> {
        val block =
            blocks.firstOrNull { it.id == target } as? FlowDesignBlock ?: throw InvalidEditException("design block $target not found")
        if (block.kind == "settings") throw InvalidEditException("design settings cannot be deleted or moved")
        val parent =
            when (block.kind) {
                "screen" -> "screen"
                "state" -> "state"
                else -> return listOf(block)
            }
        return listOf(block) + blocks.filterIsInstance<FlowDesignBlock>().filter { it.string(parent) == block.designId }
    }

    private fun move(
        blocks: List<Block>,
        operation: EditOperation.Move,
    ): List<Block> {
        val moving = family(blocks, operation.target)
        val rest = blocks - moving.toSet()
        val at = offset(rest, operation.position)
        return rest.take(at) + moving + rest.drop(at)
    }

    private fun offset(
        blocks: List<Block>,
        position: BlockPosition,
    ): Int =
        when (position) {
            BlockPosition.Start -> {
                1
            }

            BlockPosition.End -> {
                blocks.size
            }

            is BlockPosition.Before -> {
                index(blocks, position.id).also {
                    if (it == 0) throw InvalidEditException("settings must remain first")
                }
            }

            is BlockPosition.After -> {
                index(blocks, position.id) + 1
            }
        }

    private fun index(
        blocks: List<Block>,
        id: BlockId,
    ): Int = blocks.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: throw InvalidEditException("invalid design block destination $id")

    private fun insert(
        blocks: List<Block>,
        operation: EditOperation.Insert,
        index: Int,
    ): List<Block> {
        if (operation.blocks.any { it !is FlowDesignBlock }) throw InvalidEditException("insert a flow-design block")
        val at = offset(blocks, operation.position)
        val position = blocks.getOrNull(at)?.let { BlockPosition.Before(it.id) } ?: BlockPosition.End
        return DocumentEditor.applyOrdinary(blocks, operation.copy(position = position), index)
    }

    private fun replace(
        blocks: List<Block>,
        operation: EditOperation.Replace,
        index: Int,
    ): List<Block> {
        val next =
            operation.blocks.singleOrNull() as? FlowDesignBlock ?: throw InvalidEditException("replace one flow-design block at a time")
        val old = blocks.firstOrNull { it.id == operation.target } as? FlowDesignBlock
        if (old?.kind != next.kind ||
            old.designId != next.designId
        ) {
            throw InvalidEditException("a design replacement must keep its model id and kind")
        }
        return DocumentEditor.applyOrdinary(blocks, operation, index)
    }
}
