package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.McpToolResult
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext

// One MCP tool. Every call runs with the connection's own access, through the same use cases the REST API uses.
interface McpTool {
    val definition: ToolDefinition

    fun call(
        arguments: Arguments,
        context: WriteContext,
    ): McpToolResult
}
