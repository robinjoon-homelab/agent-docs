package xyz.robinjoon.agentdocs.workspace.domain.document

object FlowWarnings {
    fun of(blocks: List<Block>): List<FlowWarning> {
        val settings = blocks.firstOrNull() as? FlowSettings ?: return emptyList()
        val screens = blocks.filterIsInstance<FlowScreen>().map { it.screenId }.toSet()
        var frame = FlowFrame()
        return blocks.flatMap { block ->
            if (block is FlowScreen) frame = block.frame
            if (block is FlowElement) element(block, screens, frame.width(settings.frame)) else emptyList()
        }
    }

    private fun element(
        block: FlowElement,
        screens: Set<String>,
        width: Int,
    ): List<FlowWarning> =
        buildList {
            if (block.behavior.annotated && block.text.desc.isBlank()) add(FlowWarning(block.id, "missingDescription"))
            if (block.behavior.actions.any { it.to != null && it.to !in screens }) add(FlowWarning(block.id, "missingScreen"))
            if (block.placement.x + block.placement.w > width) add(FlowWarning(block.id, "outsideFrame"))
        }
}
