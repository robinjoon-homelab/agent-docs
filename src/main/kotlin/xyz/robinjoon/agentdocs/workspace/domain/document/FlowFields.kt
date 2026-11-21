package xyz.robinjoon.agentdocs.workspace.domain.document

object FlowFields {
    fun write(block: Block): List<Pair<String, String>> =
        when (block) {
            is FlowSettings -> {
                listOf("frame" to block.frame, "idPrefix" to block.idPrefix, "nextScreen" to block.nextScreen.toString())
            }

            is FlowScreen -> {
                listOf(
                    "screenId" to block.screenId,
                    "name" to block.name,
                    "entry" to block.entry,
                    "frame" to (block.frame.preset ?: ""),
                    "frameW" to (block.frame.width?.toString() ?: ""),
                    "frameH" to (block.frame.height?.toString() ?: ""),
                    "desc" to block.desc,
                )
            }

            is FlowElement -> {
                element(block)
            }

            else -> {
                throw InvalidDocumentException("not a flow block")
            }
        }

    private fun element(block: FlowElement): List<Pair<String, String>> =
        listOf(
            "kind" to block.appearance.kind,
            "label" to block.text.label,
            "value" to block.text.value,
            "look" to (block.appearance.look ?: ""),
            "hasDate" to (block.appearance.date != null).toString(),
            "date" to (block.appearance.date ?: ""),
            "x" to block.placement.x.toString(),
            "y" to block.placement.y.toString(),
            "w" to block.placement.w.toString(),
            "h" to block.placement.h.toString(),
            "pinned" to block.behavior.pinned.toString(),
            "group" to (block.behavior.group ?: ""),
            "annotated" to block.behavior.annotated.toString(),
            "desc" to block.text.desc,
        ) +
            block.appearance.items
                .orEmpty()
                .map { "item" to it } +
            block.behavior.actions.flatMap { listOf("when" to it.whenText, "to" to (it.to ?: ""), "mode" to it.mode) } +
            block.behavior.states.map { "state" to it }
}
