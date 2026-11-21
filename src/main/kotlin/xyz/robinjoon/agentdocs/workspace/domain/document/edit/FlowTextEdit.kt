package xyz.robinjoon.agentdocs.workspace.domain.document.edit

import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowElement
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowScreen
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown

internal object FlowTextEdit {
    fun apply(
        blocks: List<Block>,
        operation: EditOperation.ReplaceText,
    ): List<Block> {
        val target = blocks.firstOrNull { it.id == operation.target } ?: throw InvalidEditException("flow block not found")
        val texts =
            when (target) {
                is FlowScreen -> listOf(target.name, target.desc)
                is FlowElement -> listOf(target.text.label, target.text.desc, target.text.value) + target.appearance.visibleText()
                else -> throw InvalidEditException("replaceText applies to screen descriptions or element descriptions and visible text")
            }
        if (operation.find.isEmpty()) throw InvalidEditException("find must not be empty")
        val count = texts.sumOf { text -> text.windowed(operation.find.length).count { it == operation.find } }
        if (count == 0) throw EditConflictException(listOf(BlockConflict(target.id, BlockMarkdown.hash(target), BlockMarkdown.of(target))))
        if (count != 1) throw InvalidEditException("find must occur exactly once")
        val changed = replacement(target, operation)
        return blocks.map { if (it.id == target.id) changed else it }
    }

    private fun replacement(
        target: Block,
        operation: EditOperation.ReplaceText,
    ): Block =
        when (target) {
            is FlowScreen -> {
                target.copy(
                    name = target.name.replace(operation.find, operation.replacement),
                    desc = target.desc.replace(operation.find, operation.replacement),
                )
            }

            is FlowElement -> {
                target.copy(
                    appearance = target.appearance.replaceVisible(operation.find, operation.replacement),
                    text =
                        target.text.copy(
                            label = target.text.label.replace(operation.find, operation.replacement),
                            desc = target.text.desc.replace(operation.find, operation.replacement),
                            value = target.text.value.replace(operation.find, operation.replacement),
                        ),
                )
            }

            else -> {
                throw InvalidEditException("unsupported flow text")
            }
        }
}
