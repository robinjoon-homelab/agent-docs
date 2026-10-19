package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.McpToolExceptionHandler
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext

// Streamable HTTP without server push, for both protocol eras on one endpoint. A request whose params._meta names a
// protocol version is the stateless 2026-07-28 protocol; anything else, initialize included, is the 2025 lifecycle
// that Codex still uses. Every request is answered with one JSON body.
class McpProtocol(
    private val tools: List<McpTool>,
    private val json: ObjectMapper,
    private val settings: McpSettings,
) {
    private val byName = tools.associateBy { it.definition.name }

    fun handle(
        message: JsonNode,
        headers: McpHeaders,
        context: () -> WriteContext,
    ): McpResponse {
        if (!message.isObject) return McpResponse(BAD_REQUEST, error(null, INVALID_REQUEST, "send one JSON-RPC message per request", null))
        val id = message.get("id")?.takeUnless(JsonNode::isNull) ?: return McpResponse(ACCEPTED, null)
        val method = message.path("method").asString("")
        val params = message.path("params")
        val requested =
            params
                .path("_meta")
                .path(PROTOCOL_KEY)
                .takeIf(JsonNode::isString)
                ?.stringValue()
        versionProblem(id, requested, method, headers)?.let { return it }
        val modern = requested != null
        return when (method) {
            "initialize" -> ok(id, initialize(params), modern = false)
            "server/discover" -> ok(id, discover(), modern = true)
            "ping" -> ok(id, emptyMap(), modern)
            "tools/list" -> ok(id, toolList(modern), modern)
            "tools/call" -> call(id, params, context, modern)
            else -> McpResponse(if (modern) NOT_FOUND else OK, error(id, METHOD_NOT_FOUND, "method not found: $method", null))
        }
    }

    // Modern requests must agree with their headers and ask for a version this server speaks.
    private fun versionProblem(
        id: JsonNode,
        requested: String?,
        method: String,
        headers: McpHeaders,
    ): McpResponse? {
        if (requested == null) return null
        if (requested != MODERN) {
            return McpResponse(
                BAD_REQUEST,
                error(
                    id,
                    UNSUPPORTED_VERSION,
                    "unsupported protocol version $requested",
                    mapOf("supported" to listOf(MODERN), "requested" to requested),
                ),
            )
        }
        val mismatch =
            (headers.protocolVersion != null && headers.protocolVersion != requested) ||
                (headers.method != null && headers.method != method)
        return if (mismatch) McpResponse(BAD_REQUEST, error(id, HEADER_MISMATCH, "MCP headers do not match the message", null)) else null
    }

    private fun call(
        id: JsonNode,
        params: JsonNode,
        context: () -> WriteContext,
        modern: Boolean,
    ): McpResponse {
        val name = params.path("name").asString("")
        val tool = byName[name] ?: return McpResponse(OK, error(id, INVALID_PARAMS, "unknown tool: $name", null))
        val arguments = Arguments(params.get("arguments")?.takeIf(JsonNode::isObject) ?: json.createObjectNode())
        // Every write tool takes opId and summary; tools that only read never look at them.
        val write = { context().copy(operationId = arguments.optionalString("opId"), summary = arguments.optionalString("summary")) }
        val result = McpToolExceptionHandler.handle { tool.call(arguments, write()) }
        val text = result.text ?: json.writeValueAsString(result.structured)
        return ok(
            id,
            mapOf(
                "content" to listOf(mapOf("type" to "text", "text" to text)),
                "structuredContent" to result.structured,
                "isError" to result.isError,
            ),
            modern,
        )
    }

    private fun initialize(params: JsonNode): Map<String, Any?> {
        val asked = params.path("protocolVersion").asString("")
        return mapOf(
            "protocolVersion" to if (asked in legacyVersions) asked else legacyVersions.first(),
            "capabilities" to mapOf("tools" to mapOf("listChanged" to false)),
            "serverInfo" to serverInfo(),
            "instructions" to McpInstructions.TEXT,
        )
    }

    private fun discover(): Map<String, Any?> =
        mapOf(
            "supportedVersions" to listOf(MODERN),
            "capabilities" to mapOf("tools" to emptyMap<String, Any>()),
            "instructions" to McpInstructions.TEXT,
            "ttlMs" to TTL_MS,
            "cacheScope" to "public",
        )

    // The list is the same for every connection; what a connection may do is decided per call.
    private fun toolList(modern: Boolean): Map<String, Any?> {
        val listed = mapOf("tools" to tools.map { it.definition.toJson() })
        return if (modern) listed + mapOf("ttlMs" to TTL_MS, "cacheScope" to "public") else listed
    }

    private fun ok(
        id: JsonNode,
        result: Map<String, Any?>,
        modern: Boolean,
    ): McpResponse {
        val body = if (modern) result + mapOf("resultType" to "complete", "_meta" to mapOf(SERVER_INFO_KEY to serverInfo())) else result
        return McpResponse(OK, mapOf("jsonrpc" to "2.0", "id" to id, "result" to body))
    }

    private fun error(
        id: JsonNode?,
        code: Int,
        message: String,
        data: Map<String, Any?>?,
    ): Map<String, Any?> =
        mapOf(
            "jsonrpc" to "2.0",
            "id" to id,
            "error" to (mapOf("code" to code, "message" to message) + (data?.let { mapOf("data" to it) } ?: emptyMap())),
        )

    private fun serverInfo(): Map<String, String> =
        mapOf(
            "name" to "agent-docs",
            "title" to "Agent Docs",
            "version" to settings.serverVersion,
        )

    companion object {
        const val MODERN = "2026-07-28"
        val legacyVersions = listOf("2025-11-25", "2025-06-18", "2025-03-26")
        private const val PROTOCOL_KEY = "io.modelcontextprotocol/protocolVersion"
        private const val SERVER_INFO_KEY = "io.modelcontextprotocol/serverInfo"
        private const val TTL_MS = 3_600_000L
        private const val OK = 200
        private const val ACCEPTED = 202
        private const val BAD_REQUEST = 400
        private const val NOT_FOUND = 404
        private const val INVALID_REQUEST = -32600
        private const val METHOD_NOT_FOUND = -32601
        private const val INVALID_PARAMS = -32602
        private const val HEADER_MISMATCH = -32020
        private const val UNSUPPORTED_VERSION = -32022
    }
}
