package xyz.robinjoon.agentdocs.workspace.adapter.outbound.design

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.JsonNodeFactory

internal data class DesignOccurrenceAddress(
    val owner: String,
    val node: String,
    val via: List<JsonNode>,
) {
    companion object {
        fun from(
            source: JsonNode,
            prefix: List<JsonNode>,
        ): DesignOccurrenceAddress =
            DesignOccurrenceAddress(source.designString("owner"), source.designString("node"), prefix + source.path("via").values())
    }
}

internal data class DesignSlotContent(
    val children: JsonNode,
    val scope: DesignProjectionScope,
)

internal data class DesignProjectionScope(
    val owner: String,
    val env: DesignBindingScope,
    val via: List<JsonNode> = emptyList(),
    val slots: Map<String, DesignSlotContent> = emptyMap(),
    val componentDepth: Int = 0,
) {
    fun address(node: JsonNode): DesignOccurrenceAddress = DesignOccurrenceAddress(owner, node.designString("id"), via)

    fun mounted(
        node: JsonNode,
        inputs: DesignBindingScope,
        assigned: Map<String, DesignSlotContent>,
    ): DesignProjectionScope {
        val component = node.designString("component")
        val hop = hop().put("instance", node.designString("id")).put("component", component)
        val path = via + listOf(hop)
        return DesignProjectionScope(component, inputs, path, assigned, path.size)
    }

    fun repeated(
        node: JsonNode,
        row: JsonNode,
    ): DesignProjectionScope {
        val hop = hop().put("repeat", node.designString("id")).put("itemKey", row.designString("itemKey"))
        return copy(env = env.repeated(node, row), via = via + listOf(hop))
    }

    fun supplied(slot: DesignSlotContent): DesignProjectionScope =
        slot.scope.copy(via = slot.scope.via + via.drop(componentDepth).filter { it.has("repeat") })

    private fun hop() = JsonNodeFactory.instance.objectNode().put("owner", owner)
}

internal data class DesignProjectionEdit(
    val change: JsonNode,
    val scope: DesignProjectionScope,
    val site: List<String>,
) {
    fun target(): DesignOccurrenceAddress = DesignOccurrenceAddress.from(change.path("target"), scope.via)
}
