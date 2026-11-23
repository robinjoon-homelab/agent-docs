package xyz.robinjoon.agentdocs.workspace.application.service

import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowElement
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowScreen
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowSettings
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown

// The outline of a deployed flow document. The caller checks that the document starts with FlowSettings.
internal object FlowOutline {
    fun parts(
        document: Document,
        selected: List<Block>,
    ): Sequence<String> {
        val settings = document.blocks.first() as FlowSettings
        val counts = elementCounts(document.blocks)
        return selected.asSequence().map { block ->
            val details =
                when (block) {
                    is FlowScreen -> screenSummary(block, counts[block.id] ?: 0, settings)
                    is FlowElement -> elementSummary(block)
                    else -> "flowSettings ${settings.frame}"
                }
            // One rendering serves both the hash and the size.
            val markdown = BlockMarkdown.of(block)
            "- $details (b:${block.id} h:${BlockMarkdown.hash(block, markdown)} ${markdown.length}자)\n"
        }
    }

    fun screenSummary(
        screen: FlowScreen,
        elements: Int,
        settings: FlowSettings,
    ): String =
        "${screen.screenId} ${screen.name} · 요소 $elements · ${screen.frame.width(settings.frame)}×${screen.frame.height(settings.frame)}"

    fun elementSummary(element: FlowElement): String =
        "${element.text.label} · ${element.appearance.kind} ${element.placement.w}×${element.placement.h}"

    // Elements follow their screen, so a single pass counts them.
    fun elementCounts(blocks: List<Block>): Map<BlockId, Int> {
        val counts = LinkedHashMap<BlockId, Int>()
        var current: BlockId? = null
        for (block in blocks) {
            when (block) {
                is FlowScreen -> {
                    current = block.id
                    counts[block.id] = 0
                }

                is FlowElement -> {
                    current?.let { counts[it] = (counts[it] ?: 0) + 1 }
                }

                else -> {
                    Unit
                }
            }
        }
        return counts
    }
}
