package xyz.robinjoon.agentdocs.workspace.adapter.outbound.design

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode

// Applies a guarded detail operation to the caller's private working copy. Only the final
// projection is validated so one batch can reconnect both endpoints or set both handle axes.
internal object DesignGraphDetails {
    private val geometryCollections = setOf("contours", "vertices", "segments", "regions")
    private val textCollections = setOf("runs", "paragraphs")
    private val styleCollections = setOf("fills", "strokes", "effects")
    private val networkCollections = setOf("vertices", "segments", "regions")

    fun apply(
        node: ObjectNode,
        change: JsonNode,
        onMissing: (String) -> Unit,
    ) {
        val steps = (change.get("parts") ?: change.path("parents")).values().toList()
        val firstCollection = (steps.firstOrNull() ?: change).path("collection").stringValue()
        checkCollection(node, firstCollection)
        val target =
            locate(node, steps) ?: return onMissing("missing detail in '$firstCollection' on node '${node.path("id").stringValue()}'")
        when (change.path("op").stringValue()) {
            "detailSet", "detailMask" -> field(target.value, change)
            "detailRemove" -> target.array().remove(target.index)
            "detailInsert" -> insert(target.value, steps.isEmpty(), change, onMissing)
            "detailMove" -> move(target, change.path("at"), onMissing)
        }
    }

    private fun checkCollection(
        node: ObjectNode,
        collection: String,
    ) {
        val kind = node.path("kind").stringValue()
        if (collection in geometryCollections && kind != "vector") designGraphError("$collection requires a vector")
        if (collection in textCollections && kind != "text") designGraphError("$collection requires a text node")
    }

    private fun locate(
        node: ObjectNode,
        steps: List<JsonNode>,
    ): Member? {
        var current = Member(node)
        for ((depth, step) in steps.withIndex()) {
            val collection = step.path("collection").stringValue()
            val id = step.path("id").stringValue()
            val items = collection(current.value, collection, depth == 0)
            current = member(items, id) ?: return null
        }
        return current
    }

    private fun member(
        values: JsonNode?,
        id: String,
    ): Member? {
        if (values is ObjectNode && values.path("id").stringValue(null) == id) return Member(values)
        val list = values as? ArrayNode ?: return null
        val index = list.indexOfMember(id)
        if (index < 0) return null
        val value = list.get(index) as? ObjectNode ?: return null
        return Member(value, list, index)
    }

    private fun collection(
        value: ObjectNode,
        name: String,
        first: Boolean,
    ): JsonNode? =
        when {
            name == "textFills" && first -> value.path("style").path("text").get("fills")
            name == "textFills" -> value.path("style").get("fills")
            first && name in styleCollections -> value.path("style").get(name)
            first && name in textCollections -> value.path("text").get(name)
            first && name in networkCollections -> value.path("network").get(name)
            else -> value.get(name)
        }

    private fun field(
        member: ObjectNode,
        change: JsonNode,
    ) {
        val keys = change.path("field").stringValue().split('.')
        val value = change.get("value")
        var parent = member
        for (name in keys.dropLast(1)) {
            val existing = parent.get(name)
            if (existing != null && existing !is ObjectNode) designGraphError("detail path crosses scalar '$name'")
            if (value == null && existing == null) return
            parent = parent.withObjectProperty(name)
        }
        if (value == null) parent.remove(keys.last()) else parent.set(keys.last(), value.deepCopy())
    }

    private fun insert(
        parent: ObjectNode,
        first: Boolean,
        change: JsonNode,
        onMissing: (String) -> Unit,
    ) {
        val name = change.path("collection").stringValue()
        val at = change.path("at")
        val existing = collection(parent, name, first) as? ArrayNode
        val list =
            existing ?: emptyStyleCollection(parent, name, first && at.has("edge"))
                ?: return onMissing("missing detail collection '$name'")
        val item = change.path("item")
        val id = item.path("id").stringValue()
        if (list.indexOfMember(id) >= 0) designGraphError("duplicate inserted detail '$id' in $name")
        val index = destination(list, at) ?: return onMissing("missing detail ordering sibling")
        list.insert(index, item.deepCopy())
    }

    private fun emptyStyleCollection(
        parent: ObjectNode,
        name: String,
        mayCreate: Boolean,
    ): ArrayNode? {
        if (!mayCreate || name !in styleCollections) return null
        return parent.withObjectProperty("style").withArrayProperty(name)
    }

    private fun move(
        member: Member,
        at: JsonNode,
        onMissing: (String) -> Unit,
    ) {
        val list = member.array()
        val sibling = at.get("before") ?: at.get("after")
        if (sibling != null && member.value.get("id") == sibling) designGraphError("a detail cannot be its own ordering sibling")
        val destination = destination(list, at) ?: return onMissing("missing detail ordering sibling")
        list.remove(member.index)
        val adjusted = if (member.index < destination) destination - 1 else destination
        list.insert(adjusted, member.value)
    }

    private fun destination(
        list: ArrayNode,
        at: JsonNode,
    ): Int? {
        if (at.has("edge")) return if (at.path("edge").stringValue() == "first") 0 else list.size()
        val key = at.get("before") ?: at.path("after")
        val index = list.indexOfMember(key.stringValue())
        if (index < 0) return null
        return if (at.has("after")) index + 1 else index
    }

    private fun ArrayNode.indexOfMember(id: String): Int = values().indexOfFirst { it.path("id").stringValue(null) == id }

    private class Member(
        val value: ObjectNode,
        private val list: ArrayNode? = null,
        val index: Int = -1,
    ) {
        fun array(): ArrayNode = list ?: designGraphError("single-valued details cannot be removed or reordered")
    }
}
