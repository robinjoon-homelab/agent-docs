package xyz.robinjoon.agentdocs.workspace.domain.document

object FlowInsertion {
    fun prepare(
        existing: List<Block>,
        inserted: List<Block>,
        at: Int,
        defaults: Map<BlockId, FlowDefaults>,
    ): List<Block> {
        val settings = existing.first() as FlowSettings
        var next = settings.nextScreen
        val ids = existing.filterIsInstance<FlowScreen>().map { it.screenId }.toMutableSet()
        var frame =
            existing
                .take(at)
                .filterIsInstance<FlowScreen>()
                .lastOrNull()
                ?.frame ?: FlowFrame()
        return inserted.map { block ->
            val hint = defaults[block.id] ?: FlowDefaults()
            when (block) {
                is FlowScreen -> {
                    val assigned = if (hint.screenId || block.screenId in ids) screenId(settings, next++) else block.screenId
                    ids += assigned
                    next = maxOf(next, nextNumber(assigned))
                    frame = block.frame
                    block.copy(screenId = assigned)
                }

                is FlowElement -> {
                    positioned(block, hint, frame, settings)
                }

                else -> {
                    block
                }
            }
        }
    }

    private fun nextNumber(id: String): Int {
        val number = id.substringAfter('-').toIntOrNull() ?: throw InvalidDocumentException("screen number is too large")
        if (number == Int.MAX_VALUE) throw InvalidDocumentException("screen number is too large")
        return number + 1
    }

    private fun screenId(
        settings: FlowSettings,
        number: Int,
    ): String = "${settings.idPrefix.ifEmpty { "S" }}-${number.toString().padStart(2, '0')}"

    private fun positioned(
        block: FlowElement,
        hint: FlowDefaults,
        frame: FlowFrame,
        settings: FlowSettings,
    ): FlowElement {
        val bar = block.appearance.kind == "bar"
        val width = frame.width(settings.frame)
        val placement = block.placement
        val w = if (bar && hint.x) width else placement.w
        val x = if (hint.x) maxOf(0, (width - w) / 2) else placement.x
        val y = if (hint.y) (if (bar) 0 else frame.height(settings.frame) / 3) else placement.y
        return block.copy(placement = FlowPlacement(x, y, w, placement.h))
    }
}
