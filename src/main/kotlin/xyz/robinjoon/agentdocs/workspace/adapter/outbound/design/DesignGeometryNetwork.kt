package xyz.robinjoon.agentdocs.workspace.adapter.outbound.design

import tools.jackson.databind.JsonNode

internal object DesignGeometryNetwork {
    fun validate(network: JsonNode) {
        listOf("vertices", "segments", "regions").forEach { uniqueDesignFields(network.path(it), "id", "network $it") }
        val vertices =
            network
                .path("vertices")
                .values()
                .map { it.path("id").stringValue() }
                .toSet()
        val segments = network.path("segments").associateBy { it.path("id").stringValue() }
        segments.values.forEach { requireEndpoints(it, vertices) }
        network.path("regions").forEach { region -> region.path("loops").forEach { checkLoop(it, segments) } }
    }

    private fun requireEndpoints(
        segment: JsonNode,
        vertices: Set<String>,
    ) {
        if (segment.path("from").stringValue() !in vertices || segment.path("to").stringValue() !in vertices) {
            designGraphError("network segment '${segment.path("id").stringValue()}' has a missing endpoint")
        }
    }

    private fun checkLoop(
        loop: JsonNode,
        segments: Map<String, JsonNode>,
    ) {
        val edges = loop.values().map { orientedEdge(it, segments) }
        if (edges.indices.any { edges[it].second != edges[(it + 1) % edges.size].first }) {
            designGraphError("a network region requires a continuous, closed sequence of directed segments")
        }
    }

    private fun orientedEdge(
        edge: JsonNode,
        segments: Map<String, JsonNode>,
    ): Pair<String, String> {
        val id = edge.path("segment").stringValue()
        val segment = segments[id] ?: designGraphError("missing segment '$id' in network region")
        val start = segment.path("from").stringValue()
        val end = segment.path("to").stringValue()
        return if (edge.path("reverse").booleanValue(false)) end to start else start to end
    }
}
