package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.web.authentication.AuthenticationFailureHandler

// The login page explains an account that is not on the allowlist; anything else is a generic failure.
class SignInFailureHandler : AuthenticationFailureHandler {
    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException,
    ) {
        val refused = (exception as? OAuth2AuthenticationException)?.error?.errorCode == GitHubUserService.NOT_ALLOWED
        response.sendRedirect(if (refused) "/login?error=not_allowed" else "/login?error=failed")
    }
}
