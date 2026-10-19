package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.filter.OncePerRequestFilter

// Spring loads the CSRF token only when something reads it, and only then writes the XSRF-TOKEN cookie. Reading it on
// every response gives the web app the cookie before its first write.
class CsrfCookieFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        (request.getAttribute(CsrfToken::class.java.name) as? CsrfToken)?.let { checkNotNull(it.token) }
        chain.doFilter(request, response)
    }
}
