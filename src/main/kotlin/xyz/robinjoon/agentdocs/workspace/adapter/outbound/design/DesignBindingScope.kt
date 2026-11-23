package xyz.robinjoon.agentdocs.workspace.adapter.outbound.design

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.JsonNodeFactory

internal data class DesignBindingScope(
    val props: Map<String, JsonNode> = emptyMap(),
    val values: Map<String, JsonNode> = emptyMap(),
    private val fields: Map<String, Map<String, JsonNode>> = emptyMap(),
    private val rows: Map<String, JsonNode> = emptyMap(),
) {
    fun declaration(binding: JsonNode): JsonNode? {
        if (binding.has("prop")) return props[binding.designString("prop")]
        return fields[binding.designString("repeat")]?.get(binding.designString("field"))
    }

    fun resolve(value: JsonNode): JsonNode? {
        if (!value.isDesignBinding()) return value
        val prop = declaration(value)
        val explicit = if (value.has("prop")) values[value.designString("prop")] else itemValue(value)
        return explicit ?: prop?.get("initial") ?: defaultFor(prop)
    }

    fun repeated(
        node: JsonNode,
        row: JsonNode? = null,
    ): DesignBindingScope {
        val fieldSet = declaration(node.path("items"))?.path("fields")?.associateBy { it.designString("id") }.orEmpty()
        val id = node.designString("id")
        val nestedRows = if (row == null) rows else rows + (id to row)
        return copy(fields = fields + (id to fieldSet), rows = nestedRows)
    }

    private fun itemValue(binding: JsonNode): JsonNode? =
        rows[binding.designString("repeat")]?.path("fields")?.get(binding.designString("field"))

    companion object {
        fun defaultFor(property: JsonNode?): JsonNode? =
            when (property?.designString("type")) {
                "list" -> JsonNodeFactory.instance.arrayNode()
                "boolean" -> JsonNodeFactory.instance.booleanNode(false)
                else -> null
            }
    }
}
