package xyz.robinjoon.agentdocs.workspace.adapter.outbound.design

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.ObjectNode

// Patches are applied to a detached occurrence. A component's canonical source stays unchanged.
internal class DesignProjectionPatch(
    private val source: JsonNode,
) {
    var changed: ObjectNode? = null
        private set

    fun writable(): ObjectNode {
        val existing = changed
        if (existing != null) return existing
        val copy = (source as ObjectNode).deepCopy()
        changed = copy
        return copy
    }

    fun fields(fields: JsonNode) {
        fields.properties().filterNot { it.key == "inputs" }.forEach { (name, value) -> set(writable(), name, value) }
    }

    fun leaf(
        path: String,
        value: JsonNode?,
    ) {
        val keys = path.split('.')
        var parent = writable()
        for (key in keys.dropLast(1)) {
            val old = parent.get(key)
            if (value == null && old !is ObjectNode) return
            parent = descend(parent, key)
        }
        val name = keys.last()
        if (value == null) parent.without(name) else parent.set(name, value.deepCopy())
    }

    fun run(
        change: JsonNode,
        missing: (String) -> Unit,
    ) {
        val text = writable().path("text")
        val ranges = text.path("runs").asSequence() + text.path("paragraphs").asSequence().flatMap { it.path("runs").asSequence() }
        val range = ranges.singleOrNull { it.get("id") == change.get("run") } as? ObjectNode
        if (range == null) return missing("unknown or ambiguous text run")
        change.path("fields").properties().forEach { (name, value) -> set(range, name, value) }
    }

    private fun descend(
        parent: ObjectNode,
        name: String,
    ): ObjectNode {
        val existing = parent.get(name)
        if (existing is ObjectNode) return existing
        val child = JsonNodeFactory.instance.objectNode()
        if (name == "overflow" && existing?.isString == true) child.set("x", existing).set("y", existing)
        parent.set(name, child)
        return child
    }

    private fun set(
        parent: ObjectNode,
        name: String,
        value: JsonNode,
    ) {
        val base = parent.get(name)
        if (base is ObjectNode && value.isObject && combinable(base, value)) {
            value.properties().forEach { (field, child) -> set(base, field, child) }
        } else {
            parent.set(name, value.deepCopy())
        }
    }

    private fun combinable(
        original: JsonNode,
        patch: JsonNode,
    ): Boolean {
        if (original.isDesignBinding() || patch.isDesignBinding() || original.has("token") || patch.has("token")) return false
        if (patch.has("kind") && patch.get("kind") != original.get("kind")) return false
        if (!patch.has("mode") || patch.get("mode") == original.get("mode")) return true
        return original.designString("mode") in flex && patch.designString("mode") in flex
    }

    private companion object {
        val flex = setOf("row", "column")
    }
}
