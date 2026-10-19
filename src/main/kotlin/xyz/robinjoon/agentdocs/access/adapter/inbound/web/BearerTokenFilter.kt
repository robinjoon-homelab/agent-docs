package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
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
            callers.callerFor(header.substring(PREFIX.length).trim())?.let { caller ->
                val strategy = SecurityContextHolder.getContextHolderStrategy()
                val context = strategy.createEmptyContext()
                context.authentication = CallerAuthentication(caller)
                strategy.context = context
            }
        }
        chain.doFilter(request, response)
    }

    private companion object {
        const val PREFIX = "Bearer "
    }
}
