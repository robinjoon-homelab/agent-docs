package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import xyz.robinjoon.agentdocs.access.application.model.AccessSettings

// Discovery documents for MCP clients: the protected resource metadata (RFC 9728) at both the root and the
// path-suffixed location, since clients try either, and the authorization server metadata (RFC 8414). A client uses
// a metadata document client id only when the server says it supports one and accepts public clients.
@RestController
class OAuthMetadataController(
    private val settings: AccessSettings,
) {
    @GetMapping("/.well-known/oauth-protected-resource", "/.well-known/oauth-protected-resource/mcp")
    fun protectedResource(): Map<String, Any> =
        mapOf(
            "resource" to settings.resource,
            "authorization_servers" to listOf(settings.issuer),
            "scopes_supported" to scopes,
            "bearer_methods_supported" to listOf("header"),
            "resource_name" to "Agent Docs",
        )

    @GetMapping("/.well-known/oauth-authorization-server")
    fun authorizationServer(): Map<String, Any> {
        val base = settings.issuer.trimEnd('/')
        return mapOf(
            "issuer" to settings.issuer,
            "authorization_endpoint" to "$base/oauth/authorize",
            "token_endpoint" to "$base/oauth/token",
            "registration_endpoint" to "$base/oauth/register",
            "response_types_supported" to listOf("code"),
            "grant_types_supported" to listOf("authorization_code", "refresh_token"),
            "code_challenge_methods_supported" to listOf("S256"),
            "token_endpoint_auth_methods_supported" to listOf("none"),
            "scopes_supported" to scopes,
            "client_id_metadata_document_supported" to true,
            "authorization_response_iss_parameter_supported" to true,
        )
    }

    private companion object {
        val scopes = listOf("read", "comment", "edit")
    }
}
