package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.security.web.context.SecurityContextRepository

// Keeps only the Caller in the session, not GitHub's login result, and returns to where sign-in started.
class SignInSuccessHandler(
    private val contexts: SecurityContextRepository,
) : AuthenticationSuccessHandler {
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val user = authentication.principal as? CallerUser
        if (user == null) {
            response.sendRedirect("/login?error=failed")
            return
        }
        val context = SecurityContextHolder.getContextHolderStrategy().createEmptyContext()
        context.authentication = CallerAuthentication(user.caller)
        SecurityContextHolder.getContextHolderStrategy().context = context
        contexts.saveContext(context, request, response)
        response.sendRedirect(NextPath.take(request) ?: "/")
    }
}
