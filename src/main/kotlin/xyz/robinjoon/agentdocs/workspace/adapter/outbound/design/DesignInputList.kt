package xyz.robinjoon.agentdocs.workspace.adapter.outbound.design

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode

internal class DesignInputList(
    private val rules: DesignInputRules,
    private val edit: DesignProjectionEdit,
    private val fact: DesignInputFact,
) {
    private val op = edit.change

    fun apply(
        rec: DesignOccurrence,
        prop: JsonNode,
    ) {
        val id = prop.designString("id")
        val list = rec.inputs[id] as? ArrayNode ?: return rules.facts.invalid("list operation requires a list property")
        val pair = nested(prop, list) ?: return
        val current = pair.second
        val itemKey = (op.get("item") ?: op).designString("itemKey")
        val index = current.indexOfFirst { it.designString("itemKey") == itemKey }
        when (op.designString("op")) {
            "itemInsert" -> insert(pair, index)
            else -> modify(pair, index)
        }
    }

    private fun nested(
        prop: JsonNode,
        rows: ArrayNode,
    ): Pair<JsonNode, ArrayNode>? {
        var current = prop to rows
        for (step in op.path("within")) {
            val row = current.second.firstOrNull { it.get("itemKey") == step.get("itemKey") }
            val next = declaration(current.first, step.designString("field"))
            if (row == null || next == null) return missing("unknown nested list field or item")
            if (next.designString("type") != "list") return invalid("nested field must be a list")
            val fields = row.get("fields") as? ObjectNode ?: return invalid("list item requires fields")
            current = next to fields.withArrayProperty(step.designString("field"))
        }
        return current
    }

    private fun insert(
        pair: Pair<JsonNode, ArrayNode>,
        index: Int,
    ) {
        if (index >= 0) return rules.facts.invalid("duplicate inserted list item key")
        val item = op.path("item")
        val destination = place(pair.second) ?: return
        item.path("fields").properties().forEach { (id, v) -> rules.field(declaration(pair.first, id), v, edit.scope.env, fact) }
        pair.second.insert(destination, item.deepCopy())
    }

    private fun modify(
        pair: Pair<JsonNode, ArrayNode>,
        index: Int,
    ) {
        if (index < 0) return rules.facts.missing(fact, "unknown list item key")
        when (op.designString("op")) {
            "itemRemove" -> pair.second.remove(index)
            "itemMove" -> move(pair.second, index)
            "itemSet" -> set(pair.first, pair.second[index])
        }
    }

    private fun set(
        prop: JsonNode,
        row: JsonNode,
    ) {
        val key = op.designString("field")
        val value = op.path("value")
        val field = declaration(prop, key) ?: return rules.facts.missing(fact, "unknown list field: $key")
        rules.input(field, value, edit.scope.env, fact)
        val target = row.path("fields") as? ObjectNode ?: return
        edit.scope.env
            .resolve(value)
            ?.let { target.set(key, it.deepCopy()) }
    }

    private fun move(
        rows: ArrayNode,
        index: Int,
    ) {
        val at = op.path("at")
        if ((at.get("before") ?: at.get("after")) == op.get("itemKey")) return rules.facts.invalid("an item cannot be its own sibling")
        val destination = place(rows) ?: return
        val item = rows.remove(index)
        rows.insert(if (destination > index) destination - 1 else destination, item)
    }

    private fun place(rows: ArrayNode): Int? {
        val at = op.path("at")
        if (at.has("edge")) return if (at.designString("edge") == "first") 0 else rows.size()
        val sibling = at.get("before") ?: at.get("after")
        val index = rows.indexOfFirst { it.get("itemKey") == sibling }
        if (index < 0) return missing("unknown list ordering sibling")
        return if (at.has("after")) index + 1 else index
    }

    private fun declaration(
        prop: JsonNode,
        name: String,
    ): JsonNode? = prop.path("fields").firstOrNull { it.designString("id") == name }

    private fun <T> missing(reason: String): T? {
        rules.facts.missing(fact, reason)
        return null
    }

    private fun <T> invalid(reason: String): T? {
        rules.facts.invalid(reason)
        return null
    }
}
