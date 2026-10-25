package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import xyz.robinjoon.agentdocs.access.application.port.input.WebSessionUseCase

// Turns GitHub's login result into a stored sign-in and its cookie, then returns to where sign-in started. Nothing
// about the person is kept in the HTTP session, so a restart or deploy does not sign anyone out.
class SignInSuccessHandler(
    private val sessions: WebSessionUseCase,
    private val cookie: SessionCookie,
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
        cookie.write(response, sessions.start(user.caller.userId))
        response.sendRedirect(NextPath.take(request) ?: "/")
    }
}
