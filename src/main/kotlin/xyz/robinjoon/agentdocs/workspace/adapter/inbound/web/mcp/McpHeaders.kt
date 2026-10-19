package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

// The MCP headers of a request that the protocol checks against the message.
class McpHeaders(
    val protocolVersion: String?,
    val method: String?,
)
