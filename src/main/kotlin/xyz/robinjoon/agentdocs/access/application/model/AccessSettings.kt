package xyz.robinjoon.agentdocs.access.application.model

// Instance settings. issuer is this server's public base URL; resource is the MCP endpoint tokens are issued for.
data class AccessSettings(
    val owner: String,
    val members: Set<String>,
    val issuer: String,
) {
    val resource: String get() = issuer.trimEnd('/') + "/mcp"
}
