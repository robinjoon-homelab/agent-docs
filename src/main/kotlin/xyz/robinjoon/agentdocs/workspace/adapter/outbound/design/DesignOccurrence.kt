package xyz.robinjoon.agentdocs.workspace.adapter.outbound.design

import tools.jackson.databind.JsonNode

internal class DesignOccurrence(
    val source: JsonNode,
    val scope: DesignProjectionScope,
    var parent: DesignOccurrence?,
    val component: JsonNode?,
) {
    val address = scope.address(source)
    internal val children = ArrayList<DesignOccurrence>()
    val props = component?.designProps().orEmpty()
    internal val inputs = HashMap<String, JsonNode>()
    internal val slots = HashMap<String, DesignSlotContent>()
    val patch = DesignProjectionPatch(source)

    init {
        props.values.forEach { p ->
            val id = p.designString("id")
            val explicit = source.path("inputs").get(id)?.let(scope.env::resolve)
            val value = explicit ?: p.get("initial") ?: DesignBindingScope.defaultFor(p)
            if (value != null) inputs[id] = value.deepCopy()
        }
        source.path("slots").properties().forEach { (id, nodes) -> slots[id] = DesignSlotContent(nodes, scope) }
    }

    fun ancestorOf(node: DesignOccurrence): Boolean = generateSequence(node) { it.parent }.any { it === this }
}

internal object DesignOccurrenceScroll {
    fun validate(
        node: JsonNode,
        rules: DesignInputFacts,
    ) {
        if (!node.has("scroll") && !node.has("clipContent")) return
        if (node.has("scroll") && node.designString("kind") != "box") rules.invalid("initial scroll requires a box")
        listOf("x", "y").forEach { axis -> validateAxis(node, axis, rules) }
    }

    private fun validateAxis(
        node: JsonNode,
        axis: String,
        rules: DesignInputFacts,
    ) {
        val value = node.path("style").path("overflow")
        val policy = if (value.isString) value.stringValue() else value.get(axis)?.asString()
        val clip = node.get("clipContent")?.booleanValue()
        if (clip == true && policy == "visible") rules.invalid("clipContent conflicts with overflow on $axis")
        if (clip == false && policy != null && policy != "visible") rules.invalid("clipContent conflicts with overflow on $axis")
        val offset = node.path("scroll").get(axis) ?: return
        if (offset.isNumber && offset.doubleValue() == 0.0) return
        if (policy !in setOf("scroll", "auto")) rules.invalid("initial scroll requires scrollable $axis overflow")
    }
}
