package xyz.robinjoon.agentdocs.access.domain.connection

enum class ConnectionKind {
    // Approved through MCP OAuth for one client.
    AGENT,

    // Issued by the owner for a script; it has no refresh token and lasts until it expires.
    SCRIPT,
}
