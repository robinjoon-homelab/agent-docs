package xyz.robinjoon.agentdocs.workspace.adapter.outbound.design

import tools.jackson.databind.JsonNode

internal class DesignTokenRules(
    private val library: JsonNode?,
    private val missing: (String) -> Unit = {},
) {
    private val tokens = library?.path("entries")?.associateBy { it.path("id").stringValue() }.orEmpty()

    fun validateLibrary() {
        val block = library ?: return
        listOf("modes", "entries", "styles").forEach { uniqueDesignFields(block.path(it), "id", "token library $it") }
        tokens.values.forEach(::validateToken)
        block.path("styles").forEach { validateVisual(it.path("value")) }
        checkDependencies(null)
        block.path("modes").forEach { checkDependencies(it.path("id").stringValue()) }
    }

    private fun validateToken(token: JsonNode) {
        val id = token.path("id").stringValue()
        uniqueDesignFields(token.path("modes"), "mode", "mode overrides of token '$id'")
        validateVisual(token.path("value"))
        token.path("modes").forEach { validateVisual(it.path("value")) }
    }

    // Only active aliases participate in a cycle. Edges from two different themes may safely point in opposite directions.
    private fun checkDependencies(mode: String?) {
        val edges = tokens.mapValues { (_, token) -> dependencies(token, mode) }
        DesignGraphNodes.checkAcyclic(edges, "token in ${mode ?: "default mode"}")
    }

    private fun dependencies(
        token: JsonNode,
        mode: String?,
    ): Set<String> {
        val override = token.path("modes").firstOrNull { it.path("mode").stringValue() == mode }
        val value = (override ?: token).path("value")
        if (value.size() == 1 && value.has("token")) checkReference(value, token.path("type").stringValue())
        return DesignGraphNodes
            .objects(value)
            .mapNotNull { it.path("token").stringValue(null) }
            .filter(tokens::containsKey)
            .toSet()
    }

    fun validateFields(fields: JsonNode) {
        visualFields.forEach { key ->
            val value = fields.get(key) ?: return@forEach
            checkField(key, value)
            validateVisual(value)
        }
    }

    fun validateVisual(visual: JsonNode) {
        for (value in DesignGraphNodes.objects(visual)) {
            identifiedParts.forEach { uniqueDesignFields(value.path(it), "id", "design $it") }
            value.get("stops")?.takeIf(JsonNode::isArray)?.let(::validateStops)
            if (value.path("kind").stringValue(null) == "token") checkReference(value, "paint")
            value.properties().forEach { (field, data) -> checkField(field, data) }
        }
    }

    private fun validateStops(stops: JsonNode) {
        val positions = stops.values().mapNotNull { it.get("at")?.takeIf(JsonNode::isNumber)?.decimalValue() }
        if (positions.zipWithNext().any { (first, second) -> first > second }) {
            designGraphError("gradient stops must follow ascending positions")
        }
    }

    private fun checkField(
        field: String,
        value: JsonNode,
    ) {
        if (!value.has("token") || value.has("kind")) return
        val expected = tokenTypes[field] ?: return
        checkReference(value, expected)
    }

    private fun checkReference(
        value: JsonNode,
        expected: String,
    ) {
        val ref = value.path("token")
        val reference = ref.stringValue(null)
        val declared = tokens[reference]
        if (declared == null) {
            missing(reference ?: ref.designString("unresolvedName"))
            return
        }
        if (declared.path("type").stringValue() != expected) {
            designGraphError("token '$reference' must have type '$expected' at this reference")
        }
    }

    private companion object {
        val visualFields = listOf("layout", "size", "position", "style", "margin", "cell", "flexItem", "text", "ink", "geometry", "network")
        val identifiedParts = listOf("fills", "strokes", "effects", "stops", "paragraphs", "runs")
        val tokenTypes =
            mapOf(
                "color" to "color",
                "ink" to "color",
                "fill" to "color",
                "radius" to "radius",
                "shadows" to "shadow",
                "typography" to "typography",
                "gap" to "spacing",
                "rowGap" to "spacing",
                "columnGap" to "spacing",
                "top" to "spacing",
                "right" to "spacing",
                "bottom" to "spacing",
                "left" to "spacing",
                "topLeft" to "spacing",
                "topRight" to "spacing",
                "bottomLeft" to "spacing",
                "bottomRight" to "spacing",
                "start" to "spacing",
                "end" to "spacing",
                "center" to "spacing",
                "translateX" to "spacing",
                "translateY" to "spacing",
                "min" to "spacing",
                "max" to "spacing",
                "basis" to "spacing",
                "width" to "spacing",
            )
    }
}
