package xyz.robinjoon.agentdocs.workspace.adapter.outbound.design

import tools.jackson.databind.JsonNode

internal object DesignGeometryRules {
    private val geometryKinds = setOf("shape", "vector", "group", "boolean")

    fun validate(node: JsonNode) {
        when (node.path("kind").stringValue()) {
            "shape" -> validateShape(node)
            "vector" -> validateVector(node)
            "boolean" -> node.path("operands").forEach(::requireGeometry)
            "mask" -> if (node.path("mode").stringValue() == "outline") requireGeometry(node.path("source"))
        }
    }

    private fun requireGeometry(node: JsonNode) {
        val kind = node.path("kind").stringValue()
        if (kind !in geometryKinds) designGraphError("a boolean operand or outline mask source cannot include '$kind'")
        if (kind == "group") node.path("children").forEach(::requireGeometry)
    }

    private fun validateShape(node: JsonNode) {
        val shape = node.path("geometry")
        val kind = shape.path("kind").stringValue()
        val closure = shape.path("closure").stringValue(null)
        if (kind == "arc" && closure != "sector" && shape.has("innerRatio")) {
            designGraphError("an inner radius is valid only on an arc sector")
        }
        if (kind == "line" || (kind == "arc" && closure == "open")) requireCenterStrokes(node)
    }

    private fun requireCenterStrokes(node: JsonNode) {
        if (node.path("style").path("strokes").any { it.path("align").stringValue("center") != "center" }) {
            designGraphError("an open path requires center-aligned strokes")
        }
    }

    private fun validateVector(node: JsonNode) {
        node.get("network")?.let(DesignGeometryNetwork::validate)
        val contours = node.path("contours")
        uniqueDesignFields(contours, "id", "vector contours")
        if (contours.any { !it.path("closed").booleanValue() }) requireCenterStrokes(node)
        contours.forEach(::validateContour)
    }

    private fun validateContour(contour: JsonNode) {
        val id = contour.path("id").stringValue()
        val vertices = contour.path("vertices").values().toList()
        uniqueDesignFields(vertices, "id", "vertices of contour '$id'")
        if (!contour.path("closed").booleanValue() && hasOpenEndHandles(vertices)) {
            designGraphError("open contour '$id' has a control handle or arc beyond its endpoints")
        }
        vertices.forEachIndexed { index, vertex ->
            val next = vertices[(index + 1) % vertices.size]
            if (vertex.has("arcToNext") && (vertex.has("out") || next.has("in"))) {
                designGraphError("an arc in contour '$id' cannot also have Bezier control handles")
            }
        }
    }

    private fun hasOpenEndHandles(vertices: List<JsonNode>): Boolean =
        vertices.first().has("in") || vertices.last().has("out") || vertices.last().has("arcToNext")
}
