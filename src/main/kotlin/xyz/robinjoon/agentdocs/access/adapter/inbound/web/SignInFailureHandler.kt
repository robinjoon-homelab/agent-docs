package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.web.authentication.AuthenticationFailureHandler

// The login page explains an account that is not on the allowlist; anything else is a generic failure. The page cannot
// show why, so the reason goes to the log. GitHub error text can quote a response body, so tokens in it are masked.
class SignInFailureHandler : AuthenticationFailureHandler {
    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException,
    ) {
        val code = (exception as? OAuth2AuthenticationException)?.error?.errorCode
        log.warn("GitHub sign-in failed: code={} reason={}", code, reasonOf(exception))
        response.sendRedirect(if (code == GitHubUserService.NOT_ALLOWED) "/login?error=not_allowed" else "/login?error=failed")
    }

    private companion object {
        val log = LoggerFactory.getLogger(SignInFailureHandler::class.java)
        val secrets = Regex("""(gh[a-z]_)\w+|("?(?:access|refresh)_token"?\s*[:=]\s*"?)[^"&,\s}]+""")
        const val CAUSE_DEPTH = 5

        fun reasonOf(exception: Throwable): String =
            generateSequence(exception) { it.cause }
                .take(CAUSE_DEPTH)
                .joinToString(" <- ") { "${it.javaClass.simpleName}: ${it.message}" }
                .replace(secrets) { it.groupValues[1] + it.groupValues[2] + "***" }
    }
}
