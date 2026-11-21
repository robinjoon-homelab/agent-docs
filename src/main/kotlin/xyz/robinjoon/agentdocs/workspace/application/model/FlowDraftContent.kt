package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowInsertion
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowRules
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowSettings
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.ParsedBlock

object FlowDraftContent {
    fun markdown(
        parsed: List<ParsedBlock>,
        frame: String,
        prefix: String,
    ): List<Block> {
        if (parsed.isEmpty()) return FlowRules.initial(frame, prefix)
        val first = parsed.first().block
        val settings = first as? FlowSettings ?: FlowSettings(BlockId.next(), frame, prefix, 1)
        val content = if (first is FlowSettings) parsed.drop(1) else parsed
        val normalized =
            FlowInsertion.prepare(
                listOf(settings),
                content.map { it.block },
                1,
                content.associate {
                    it.block.id to
                        it.flowDefaults
                },
            )
        return listOf(settings) + normalized
    }
}
