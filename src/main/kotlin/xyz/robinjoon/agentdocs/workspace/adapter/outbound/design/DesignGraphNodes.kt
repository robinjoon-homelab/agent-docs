package xyz.robinjoon.agentdocs.workspace.adapter.outbound.design

import tools.jackson.databind.JsonNode
import xyz.robinjoon.agentdocs.workspace.domain.document.InvalidDocumentException

internal fun designGraphError(message: String): Nothing = throw InvalidDocumentException(message)

internal fun uniqueDesignFields(
    nodes: Iterable<JsonNode>,
    field: String,
    scope: String,
) {
    val seen = HashSet<String>()
    nodes.forEach { node ->
        val value = node.get(field)?.stringValue(null) ?: return@forEach
        if (!seen.add(value)) designGraphError("duplicate $field '$value' in $scope")
    }
}

internal object DesignGraphNodes {
    private val childrenFields = listOf("children", "template", "fallback", "operands")

    // Slot assignments belong to the block where they were authored, even when rendered in a different component.
    fun ownedBy(block: JsonNode): Sequence<JsonNode> =
        sequence {
            val pending = ArrayDeque<JsonNode>()
            block.get("root")?.let(pending::addLast)
            pending.addAll(insertions(block.path("changes")))
            while (pending.isNotEmpty()) {
                val node = pending.removeLast()
                yield(node)
                pending.addAll(children(node))
                pending.addAll(insertions(node.path("overrides")))
            }
        }

    private fun children(node: JsonNode): Sequence<JsonNode> =
        childrenFields.asSequence().flatMap { node.path(it).asSequence() } +
            listOfNotNull(node.get("source")).asSequence() +
            node
                .path("slots")
                .values()
                .asSequence()
                .flatMap { it.asSequence() }

    private fun insertions(changes: JsonNode): Sequence<JsonNode> =
        changes.asSequence().flatMap { change ->
            listOfNotNull(change.get("node")).asSequence() + change.path("assignment").path("children").asSequence()
        }

    fun objects(root: JsonNode): Sequence<JsonNode> =
        sequence {
            val pending = ArrayDeque<JsonNode>()
            pending.addLast(root)
            while (pending.isNotEmpty()) {
                val node = pending.removeLast()
                if (node.isObject) yield(node)
                pending.addAll(node.values().filter(JsonNode::isContainer))
            }
        }

    // Resolve dependencies without recursively expanding a possibly very deep component or token graph.
    fun checkAcyclic(
        dependencies: Map<String, Set<String>>,
        kind: String,
    ) {
        val counts = dependencies.mapValues { it.value.size }.toMutableMap()
        val dependents = HashMap<String, MutableList<String>>()
        dependencies.forEach { (from, links) ->
            links.forEach { to -> dependents.getOrPut(to) { ArrayList() }.add(from) }
        }
        val ready = ArrayDeque(counts.filterValues { it == 0 }.keys)
        while (ready.isNotEmpty()) {
            val id = ready.removeLast()
            counts.remove(id)
            ready.addAll(
                dependents[id].orEmpty().filter { dependent ->
                    val remaining = counts.getValue(dependent) - 1
                    counts[dependent] = remaining
                    remaining == 0
                },
            )
        }
        if (counts.isNotEmpty()) designGraphError("cyclic $kind dependency involving '${counts.keys.first()}'")
    }
}
