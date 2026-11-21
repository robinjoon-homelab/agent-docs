package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowElement
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowScreen
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowSettings

object FlowBlockData {
    fun type(block: Block): String =
        when (block) {
            is FlowSettings -> "flowSettings"
            is FlowScreen -> "flowScreen"
            is FlowElement -> "flowElement"
            else -> error("not a flow block")
        }

    fun fields(block: Block): Map<String, Any?> =
        when (block) {
            is FlowSettings -> {
                mapOf("frame" to block.frame, "idPrefix" to block.idPrefix, "nextScreen" to block.nextScreen)
            }

            is FlowScreen -> {
                mapOf(
                    "screenId" to block.screenId,
                    "name" to block.name,
                    "entry" to block.entry,
                    "frame" to block.frame.preset,
                    "frameW" to block.frame.width,
                    "frameH" to block.frame.height,
                    "desc" to block.desc,
                )
            }

            is FlowElement -> {
                element(block)
            }

            else -> {
                error("not a flow block")
            }
        }

    private fun element(block: FlowElement): Map<String, Any?> =
        mapOf(
            "kind" to block.appearance.kind,
            "look" to block.appearance.look,
            "items" to block.appearance.items,
            "date" to block.appearance.date,
            "label" to block.text.label,
            "value" to block.text.value,
            "desc" to block.text.desc,
            "x" to block.placement.x,
            "y" to block.placement.y,
            "w" to block.placement.w,
            "h" to block.placement.h,
            "pinned" to block.behavior.pinned,
            "group" to block.behavior.group,
            "annotated" to block.behavior.annotated,
            "actions" to block.behavior.actions.map { mapOf("when" to it.whenText, "to" to it.to, "mode" to it.mode) },
            "states" to block.behavior.states,
        )
}
