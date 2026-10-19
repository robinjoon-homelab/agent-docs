package xyz.robinjoon.agentdocs.workspace.domain.document.edit

import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.TableBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.MarkdownReader

// Applies a batch of block operations all or nothing. Every guarded block is checked against the state the writer
// read before anything is applied, so one stale block stops the whole batch and every conflict is reported at once.
object DocumentEditor {
    const val MAX_OPERATIONS = 200

    fun apply(
        blocks: List<Block>,
        operations: List<EditOperation>,
    ): List<Block> {
        if (operations.isEmpty() || operations.size > MAX_OPERATIONS) {
            throw InvalidEditException("an edit has 1 to $MAX_OPERATIONS operations")
        }
        requireUnchanged(blocks, operations)
        return operations.foldIndexed(blocks) { index, current, operation -> applyOne(current, operation, index) }
    }

    private fun requireUnchanged(
        blocks: List<Block>,
        operations: List<EditOperation>,
    ) {
        val byId = blocks.associateBy { it.id }
        val conflicts =
            operations
                .mapNotNull(::guardOf)
                .mapNotNull { (id, expected) -> conflictOf(byId[id], id, expected) }
                .distinctBy { it.blockId }
        if (conflicts.isNotEmpty()) {
            throw EditConflictException(conflicts)
        }
    }

    private fun guardOf(operation: EditOperation): Pair<BlockId, String>? =
        when (operation) {
            is EditOperation.Replace -> operation.target to operation.expectedHash
            is EditOperation.Delete -> operation.target to operation.expectedHash
            is EditOperation.Move -> operation.target to operation.expectedHash
            is EditOperation.Insert, is EditOperation.ReplaceText -> null
        }

    private fun conflictOf(
        block: Block?,
        id: BlockId,
        expected: String,
    ): BlockConflict? {
        if (block == null) return BlockConflict(id, null, null)
        val hash = BlockMarkdown.hash(block)
        return if (hash == expected) null else BlockConflict(id, hash, BlockMarkdown.of(block))
    }

    private fun applyOne(
        blocks: List<Block>,
        operation: EditOperation,
        index: Int,
    ): List<Block> =
        when (operation) {
            is EditOperation.Replace -> replace(blocks, operation, index)
            is EditOperation.Insert -> insert(blocks, positionOf(blocks, operation.position, index), operation.blocks, index)
            is EditOperation.Delete -> blocks.filterNot { it.id == require(blocks, operation.target, index).id }
            is EditOperation.Move -> move(blocks, operation, index)
            is EditOperation.ReplaceText -> replaceText(blocks, operation, index)
        }

    private fun replace(
        blocks: List<Block>,
        operation: EditOperation.Replace,
        index: Int,
    ): List<Block> {
        val target = require(blocks, operation.target, index)
        if (operation.blocks.isEmpty()) {
            throw InvalidEditException("operation $index: a replacement needs at least one block; use delete to remove a block")
        }
        val at = blocks.indexOf(target)
        val first = keptIds(target, operation.blocks.first().withId(target.id))
        return blocks.subList(0, at) + first + fresh(operation.blocks.drop(1), blocks) + blocks.subList(at + 1, blocks.size)
    }

    private fun insert(
        blocks: List<Block>,
        at: Int,
        inserted: List<Block>,
        index: Int,
    ): List<Block> {
        if (inserted.isEmpty()) {
            throw InvalidEditException("operation $index: an insert needs at least one block")
        }
        return blocks.subList(0, at) + fresh(inserted, blocks) + blocks.subList(at, blocks.size)
    }

    private fun move(
        blocks: List<Block>,
        operation: EditOperation.Move,
        index: Int,
    ): List<Block> {
        val moving = require(blocks, operation.target, index)
        val position = operation.position
        if ((position is BlockPosition.Before && position.id == moving.id) ||
            (position is BlockPosition.After && position.id == moving.id)
        ) {
            throw InvalidEditException("operation $index: a block cannot move next to itself")
        }
        val rest = blocks.filterNot { it.id == moving.id }
        val at = positionOf(rest, position, index)
        return rest.subList(0, at) + moving + rest.subList(at, rest.size)
    }

    private fun replaceText(
        blocks: List<Block>,
        operation: EditOperation.ReplaceText,
        index: Int,
    ): List<Block> {
        val block = require(blocks, operation.target, index)
        val markdown = BlockMarkdown.of(block)
        if (operation.find.isEmpty()) throw InvalidEditException("operation $index: find text must not be empty")
        val occurrences = markdown.windowed(operation.find.length).count { it == operation.find }
        // Text that is gone was most likely changed by someone after the read, so the answer is the block as it is now.
        if (occurrences == 0) throw EditConflictException(listOf(BlockConflict(block.id, BlockMarkdown.hash(block), markdown)))
        if (occurrences > 1) {
            throw InvalidEditException("operation $index: find text must occur exactly once in block ${block.id}, found $occurrences")
        }
        val parsed = MarkdownReader.readBlocks(markdown.replace(operation.find, operation.replacement))
        val replaced = parsed.singleOrNull()?.block ?: throw InvalidEditException("operation $index: the replaced text must stay one block")
        return blocks.map { if (it.id == block.id) keptIds(block, replaced.withId(block.id)) else it }
    }

    private fun keptIds(
        earlier: Block,
        next: Block,
    ): Block = if (earlier is TableBlock && next is TableBlock) next.keepingIdsOf(earlier) else next

    private fun positionOf(
        blocks: List<Block>,
        position: BlockPosition,
        index: Int,
    ): Int =
        when (position) {
            BlockPosition.Start -> 0
            BlockPosition.End -> blocks.size
            is BlockPosition.Before -> blocks.indexOf(require(blocks, position.id, index))
            is BlockPosition.After -> blocks.indexOf(require(blocks, position.id, index)) + 1
        }

    private fun require(
        blocks: List<Block>,
        id: BlockId,
        index: Int,
    ): Block = blocks.firstOrNull { it.id == id } ?: throw InvalidEditException("operation $index: block $id not found")

    // New blocks never take an id that is already in the document, even when the writer pasted an annotation.
    private fun fresh(
        added: List<Block>,
        existing: List<Block>,
    ): List<Block> {
        val taken = existing.map { it.id }.toMutableSet()
        return added.map { block ->
            val id = if (block.id in taken) generateSequence { BlockId.next() }.first { it !in taken } else block.id
            taken += id
            block.withId(id)
        }
    }
}
