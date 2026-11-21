package xyz.robinjoon.agentdocs.workspace.application.service

import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowElement
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowScreen
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowSettings
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown

internal object FlowOutline {
    fun parts(
        document: Document,
        selected: List<Block>,
    ): Sequence<String> {
        val settings = document.blocks.first() as FlowSettings
        return selected.asSequence().map { block ->
            val details =
                when (block) {
                    is FlowScreen -> screen(document.blocks, block, settings)
                    is FlowElement -> "${block.text.label} · ${block.appearance.kind} ${block.placement.w}×${block.placement.h}"
                    else -> "flowSettings ${settings.frame}"
                }
            "- $details (b:${block.id} h:${BlockMarkdown.hash(block)} ${BlockMarkdown.of(block).length}자)\n"
        }
    }

    private fun screen(
        blocks: List<Block>,
        screen: FlowScreen,
        settings: FlowSettings,
    ): String {
        val count = blocks.drop(blocks.indexOf(screen) + 1).takeWhile { it is FlowElement }.size
        return "${screen.screenId} ${screen.name} · 요소 $count · ${screen.frame.width(
            settings.frame,
        )}×${screen.frame.height(settings.frame)}"
    }
}
