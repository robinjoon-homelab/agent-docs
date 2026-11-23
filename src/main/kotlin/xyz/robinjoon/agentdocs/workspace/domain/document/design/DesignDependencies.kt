package xyz.robinjoon.agentdocs.workspace.domain.document.design

import xyz.robinjoon.agentdocs.workspace.domain.document.FlowDesignBlock

fun FlowDesignBlock.referencedComponents(): Set<String> {
    val ids = mutableSetOf<String>()
    val stack = ArrayDeque<Any>(listOf(fields))
    while (stack.isNotEmpty()) {
        when (val value = stack.removeLast()) {
            is Map<*, *> -> {
                instanceId(value)?.let(ids::add)
                stack.addAll(value.values.filterNotNull())
            }

            is List<*> -> {
                stack.addAll(value.filterNotNull())
            }
        }
    }
    return ids
}

private fun instanceId(node: Map<*, *>): String? = if (node["kind"] == "instance") node["component"] as? String else null

/** Rendering one screen needs its slot contents, selected state, nested components and document-wide styles. */
fun DesignScene.renderBlocks(blocks: List<FlowDesignBlock>): List<FlowDesignBlock> {
    val components = blocks.filter { it.kind == "component" }.associateBy { it.designId }
    val used = mutableSetOf<String>()
    val queue = ArrayDeque(listOfNotNull(screen, state))
    while (queue.isNotEmpty()) {
        val next = queue.removeFirst()
        if (used.add(next.designId)) queue.addAll(next.referencedComponents().mapNotNull(components::get))
    }
    return blocks.filter {
        when (it.kind) {
            "screen", "state", "component" -> it.designId in used
            "annotation" -> it.string("screen") == screen.designId && (it.string("state") == null || it.string("state") == state?.designId)
            else -> true
        }
    }
}
