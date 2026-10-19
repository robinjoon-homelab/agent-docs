package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import xyz.robinjoon.agentdocs.access.application.model.AccessSettings

// An unauthenticated API or MCP request gets 401. The WWW-Authenticate header points MCP clients at the protected
// resource metadata, which is how they find the authorization server (RFC 9728).
class ApiAuthenticationEntryPoint(
    private val settings: AccessSettings,
) : AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException,
    ) {
        val metadata = settings.issuer.trimEnd('/') + "/.well-known/oauth-protected-resource/mcp"
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.setHeader("WWW-Authenticate", "Bearer resource_metadata=\"$metadata\"")
        response.contentType = "application/problem+json"
        response.writer.write(
            """{"title":"Unauthorized","status":401,"code":"unauthenticated","fix":"sign in or connect through MCP OAuth"}""",
        )
    }
}
