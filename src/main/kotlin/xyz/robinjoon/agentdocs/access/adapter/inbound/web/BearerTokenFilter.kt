package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import xyz.robinjoon.agentdocs.access.application.model.UploadToken
import xyz.robinjoon.agentdocs.access.application.port.input.CallerUseCase

// Authenticates a request that carries a connection token. The result is never stored in a session, so every request
// is checked against the connection as it is now. An unknown token leaves the request unauthenticated.
class BearerTokenFilter(
    private val callers: CallerUseCase,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith(PREFIX, ignoreCase = true)) {
            val token = header.substring(PREFIX.length).trim()
            // An upload token counts only on the one endpoint, where its secret was checked before this filter.
            val caller = if (request.requestURI == UploadToken.ENDPOINT) callers.callerForUpload(token) else callers.callerFor(token)
            caller?.let {
                val strategy = SecurityContextHolder.getContextHolderStrategy()
                val context = strategy.createEmptyContext()
                context.authentication = CallerAuthentication(it)
                strategy.context = context
            }
        }
        chain.doFilter(request, response)
    }

    private companion object {
        const val PREFIX = "Bearer "
    }
}
