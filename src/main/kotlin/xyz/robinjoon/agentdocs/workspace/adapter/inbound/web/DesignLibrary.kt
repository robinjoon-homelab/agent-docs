package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import xyz.robinjoon.agentdocs.workspace.domain.document.FlowDesignBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.design.referencedComponents

internal class DesignLibrary(
    private val blocks: List<FlowDesignBlock>,
    private val request: DesignRequest,
    private val hash: (FlowDesignBlock) -> String,
) {
    private val query = request.optionalString("query").orEmpty()
    private val kind = request.optionalString("kind")
    private val owner = request.optionalString("owner")
    private val dependents by lazy {
        buildMap<String, MutableSet<FlowDesignBlock>> {
            for (block in blocks) for (used in block.referencedComponents()) getOrPut(used, ::mutableSetOf).add(block)
        }
    }

    fun rows(): Sequence<Map<String, Any?>> =
        blocks
            .asSequence()
            .filter { owner == null || it.designId == owner }
            .flatMap { block ->
                when (block.kind) {
                    "component" -> component(block)
                    "tokens" -> members(block, "entries", "token")
                    else -> emptySequence()
                }
            }.filter { row ->
                (kind == null || row["kind"] == kind) &&
                    listOf(row["id"], row["name"], row["owner"]).filterIsInstance<String>().any { it.contains(query, ignoreCase = true) }
            }

    private fun component(block: FlowDesignBlock): Sequence<Map<String, Any?>> {
        val summary =
            mapOf(
                "kind" to "component",
                "id" to block.designId,
                "name" to block.string("name"),
                "block" to block.id.value,
                "hash" to hash(block),
                "screenCount" to screensUsing(block.designId),
            )
        if (owner == null) return sequenceOf(summary)
        return sequenceOf(summary) + members(block, "props", "prop") + members(block, "variants", "variant")
    }

    private fun members(
        block: FlowDesignBlock,
        field: String,
        itemKind: String,
    ): Sequence<Map<String, Any?>> {
        val values = block.fields[field] as? List<*> ?: return emptySequence()
        return values.asSequence().filterIsInstance<Map<*, *>>().map { item ->
            val result =
                mutableMapOf<String, Any?>(
                    "kind" to itemKind,
                    "id" to item["id"],
                    "owner" to block.designId,
                    "block" to block.id.value,
                    "hash" to hash(block),
                )
            for (key in listOf("name", "type", "choices", "when")) if (key in item) result[key] = item[key]
            val value = item["value"] ?: item["initial"]
            if (value is String || value is Number || value is Boolean) result["value"] = value
            result
        }
    }

    private fun screensUsing(component: String): Int {
        val visited = mutableSetOf<String>()
        val screens = mutableSetOf<String>()
        val queue = ArrayDeque(listOf(component))
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!visited.add(current)) continue
            collectUsers(current, screens, queue)
        }
        return screens.size
    }

    private fun collectUsers(
        current: String,
        screens: MutableSet<String>,
        queue: ArrayDeque<String>,
    ) {
        for (parent in dependents[current].orEmpty()) {
            when (parent.kind) {
                "screen" -> screens.add(parent.designId)
                "state" -> parent.string("screen")?.let(screens::add)
                "component" -> queue.addLast(parent.designId)
            }
        }
    }
}
