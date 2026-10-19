package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

// An HTTP status and a JSON-RPC message, or no body for an accepted notification.
class McpResponse(
    val status: Int,
    val body: Map<String, Any?>?,
)
