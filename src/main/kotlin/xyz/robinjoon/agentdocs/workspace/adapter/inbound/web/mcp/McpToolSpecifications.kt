package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.modelcontextprotocol.common.McpTransportContext
import io.modelcontextprotocol.server.McpStatelessServerFeatures
import io.modelcontextprotocol.spec.McpSchema
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.CallerAccess
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.McpToolExceptionHandler
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.McpToolResult
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.domain.document.ChangeSource
import java.security.Principal

// Hands our tools to the official MCP SDK, which owns the protocol: version negotiation, JSON-RPC framing, input
// schema validation and the error for an unknown tool. A call runs as the connection its bearer token belongs to, and
// is timed by tool and outcome (agentdocs.mcp.calls): ok, the error code such as conflict, or failed.
class McpToolSpecifications(
    private val json: ObjectMapper,
    private val meters: MeterRegistry,
) {
    fun of(tool: McpTool): McpStatelessServerFeatures.SyncToolSpecification =
        McpStatelessServerFeatures.SyncToolSpecification
            .builder()
            .tool(tool.definition.toTool())
            .callHandler { transport, request -> call(tool, transport, request) }
            .build()

    private fun call(
        tool: McpTool,
        transport: McpTransportContext,
        request: McpSchema.CallToolRequest,
    ): McpSchema.CallToolResult {
        val timing = Timer.start(meters)
        var outcome = FAILED
        try {
            val result = run(tool, transport, request)
            outcome = if (result.isError) result.data?.get("code")?.toString() ?: FAILED else OK
            return result.toCallToolResult()
        } finally {
            timing.stop(meters.timer(CALLS, "tool", tool.definition.name, "outcome", outcome))
        }
    }

    private fun run(
        tool: McpTool,
        transport: McpTransportContext,
        request: McpSchema.CallToolRequest,
    ): McpToolResult {
        val arguments = Arguments(json.valueToTree<JsonNode>(request.arguments().orEmpty()))
        val principal = transport.get(PRINCIPAL) as? Principal
        return McpToolExceptionHandler.handle {
            // Every write tool takes opId and summary; tools that only read never look at them.
            val context =
                WriteContext(
                    CallerAccess.of(principal),
                    ChangeSource.MCP,
                    arguments.optionalString("opId"),
                    arguments.optionalString("summary"),
                )
            tool.call(arguments, context)
        }
    }

    private fun McpToolResult.toCallToolResult(): McpSchema.CallToolResult =
        McpSchema.CallToolResult
            .builder()
            .content(listOf(McpSchema.TextContent(markdown ?: json.writeValueAsString(data))))
            .isError(isError)
            .build()

    companion object {
        // The transport context key under which the request's authenticated principal reaches a tool call.
        const val PRINCIPAL = "principal"
        private const val CALLS = "agentdocs.mcp.calls"
        private const val OK = "ok"
        private const val FAILED = "failed"
    }
}
