package xyz.robinjoon.agentdocs.workspace.domain.document.edit

import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowElement
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowRules
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowScreen
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowSettings

internal object FlowEditor {
    fun apply(
        blocks: List<Block>,
        operation: EditOperation,
        index: Int,
    ): List<Block> {
        val result =
            when (operation) {
                is EditOperation.Delete -> delete(blocks, operation.target)
                is EditOperation.Move -> move(blocks, operation)
                is EditOperation.Insert -> insert(blocks, operation, index)
                is EditOperation.Replace -> replace(blocks, operation, index)
                is EditOperation.ReplaceText -> FlowTextEdit.apply(blocks, operation)
            }
        FlowRules.validate("flow", result)
        return FlowRules.normalize(result, blocks)
    }

    private fun delete(
        blocks: List<Block>,
        id: BlockId,
    ): List<Block> {
        val target = find(blocks, id)
        if (target is FlowSettings) throw InvalidEditException("flow settings cannot be deleted")
        val removed = if (target is FlowScreen) screenBlocks(blocks, id).map { it.id }.toSet() else setOf(id)
        return blocks.filterNot { it.id in removed }
    }

    private fun replace(
        blocks: List<Block>,
        operation: EditOperation.Replace,
        index: Int,
    ): List<Block> {
        val target = find(blocks, operation.target)
        val replacement = operation.blocks.singleOrNull() ?: throw InvalidEditException("replace one flow block at a time")
        if (target::class != replacement::class) throw InvalidEditException("a flow replacement must keep its block type")
        val kept = keepOmitted(target, replacement, operation.flowDefaults)
        return DocumentEditor.applyOrdinary(blocks, operation.copy(blocks = listOf(kept)), index)
    }

    private fun keepOmitted(
        target: Block,
        next: Block,
        defaults: xyz.robinjoon.agentdocs.workspace.domain.document.FlowDefaults,
    ): Block =
        when {
            target is FlowScreen && next is FlowScreen && defaults.screenId -> {
                next.copy(screenId = target.screenId)
            }

            target is FlowElement && next is FlowElement -> {
                next.copy(
                    placement =
                        next.placement.copy(
                            x = if (defaults.x) target.placement.x else next.placement.x,
                            y = if (defaults.y) target.placement.y else next.placement.y,
                        ),
                )
            }

            else -> {
                next
            }
        }

    private fun insert(
        blocks: List<Block>,
        operation: EditOperation.Insert,
        index: Int,
    ): List<Block> {
        val first = operation.blocks.firstOrNull() ?: throw InvalidEditException("insert needs blocks")
        if (first is FlowElement && operation.blocks.any { it !is FlowElement }) {
            throw InvalidEditException("insert screens at screen boundaries in a separate operation")
        }
        val at = if (first is FlowScreen) screenPosition(blocks, operation.position) else elementPosition(blocks, operation.position)
        val added =
            xyz.robinjoon.agentdocs.workspace.domain.document.FlowInsertion.prepare(
                blocks,
                operation.blocks,
                at,
                operation.flowDefaults,
            )
        if (at == blocks.size) return append(blocks, added, index)
        val adjusted = EditOperation.Insert(BlockPosition.Before(blocks[at].id), added)
        return DocumentEditor.applyOrdinary(blocks, adjusted, index)
    }

    private fun append(
        blocks: List<Block>,
        added: List<Block>,
        index: Int,
    ): List<Block> = DocumentEditor.applyOrdinary(blocks, EditOperation.Insert(BlockPosition.End, added), index)

    private fun move(
        blocks: List<Block>,
        operation: EditOperation.Move,
    ): List<Block> {
        val target = find(blocks, operation.target)
        if (target is FlowSettings) throw InvalidEditException("flow settings cannot move")
        val moving = if (target is FlowScreen) screenBlocks(blocks, target.id) else listOf(target)
        val rest = blocks.filterNot { block -> moving.any { it.id == block.id } }
        val at = if (target is FlowScreen) screenPosition(rest, operation.position) else elementPosition(rest, operation.position)
        if (target is FlowElement && owner(blocks, blocks.indexOf(target)) != owner(rest, at - 1)) {
            throw InvalidEditException("elements can only move within their screen")
        }
        return rest.take(at) + moving + rest.drop(at)
    }

    private fun screenPosition(
        blocks: List<Block>,
        position: BlockPosition,
    ): Int =
        when (position) {
            BlockPosition.Start -> 1
            BlockPosition.End -> blocks.size
            is BlockPosition.Before -> screenIndex(blocks, position.id)
            is BlockPosition.After -> screenIndex(blocks, position.id) + screenBlocks(blocks, position.id).size
        }

    private fun elementPosition(
        blocks: List<Block>,
        position: BlockPosition,
    ): Int =
        when (position) {
            is BlockPosition.After -> {
                val target = find(blocks, position.id)
                if (target is FlowSettings) throw InvalidEditException("elements need a screen")
                blocks.indexOf(target) + 1
            }

            is BlockPosition.Before -> {
                val target = find(blocks, position.id)
                if (target !is FlowElement) throw InvalidEditException("elements can only be inserted before elements")
                blocks.indexOf(target)
            }

            else -> {
                throw InvalidEditException("an element needs an explicit screen or element destination")
            }
        }

    private fun screenIndex(
        blocks: List<Block>,
        id: BlockId,
    ): Int {
        val target = find(blocks, id)
        if (target !is FlowScreen) throw InvalidEditException("a screen destination must be another screen")
        return blocks.indexOf(target)
    }

    private fun screenBlocks(
        blocks: List<Block>,
        id: BlockId,
    ): List<Block> = blocks.drop(screenIndex(blocks, id)).take(1) + blocks.drop(screenIndex(blocks, id) + 1).takeWhile { it is FlowElement }

    private fun owner(
        blocks: List<Block>,
        at: Int,
    ): BlockId? =
        blocks
            .take(at + 1)
            .filterIsInstance<FlowScreen>()
            .lastOrNull()
            ?.id

    private fun find(
        blocks: List<Block>,
        id: BlockId,
    ): Block = blocks.firstOrNull { it.id == id } ?: throw InvalidEditException("flow block $id not found")
}
