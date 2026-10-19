package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

// allowedOrigins are the browser origins that may call /mcp; requests without an Origin header, as CLI clients send,
// are always allowed.
class McpSettings(
    val allowedOrigins: Set<String>,
)
