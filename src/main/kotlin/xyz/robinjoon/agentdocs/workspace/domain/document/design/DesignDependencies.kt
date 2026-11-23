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
