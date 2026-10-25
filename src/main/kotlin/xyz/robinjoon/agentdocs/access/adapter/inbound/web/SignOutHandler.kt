package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.logout.LogoutHandler
import xyz.robinjoon.agentdocs.access.application.port.input.WebSessionUseCase

// Ends this browser's sign-in on the server, not just in the browser, so a copied cookie stops working too.
class SignOutHandler(
    private val sessions: WebSessionUseCase,
    private val cookie: SessionCookie,
) : LogoutHandler {
    override fun logout(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication?,
    ) {
        cookie.read(request)?.let(sessions::end)
        cookie.clear(response)
    }
}
