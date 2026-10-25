package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import xyz.robinjoon.agentdocs.access.application.port.input.WebSessionUseCase

// Authenticates a browser from its sign-in cookie, checked against the stored sign-in on every request. A cookie whose
// sign-in ended is cleared, so the browser stops sending it.
class SessionCookieFilter(
    private val sessions: WebSessionUseCase,
    private val cookie: SessionCookie,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        cookie.read(request)?.let { authenticate(it, response) }
        chain.doFilter(request, response)
    }

    private fun authenticate(
        token: String,
        response: HttpServletResponse,
    ) {
        val signedIn = sessions.callerFor(token)
        if (signedIn == null) {
            cookie.clear(response)
            return
        }
        val strategy = SecurityContextHolder.getContextHolderStrategy()
        val context = strategy.createEmptyContext()
        context.authentication = CallerAuthentication(signedIn.caller)
        strategy.context = context
        // The browser counts the cookie's lifetime from when it last received it, so a renewal sends it again.
        if (signedIn.renewed) cookie.write(response, token)
    }
}
