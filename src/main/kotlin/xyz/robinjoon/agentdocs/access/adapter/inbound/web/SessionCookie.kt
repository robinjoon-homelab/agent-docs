package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import xyz.robinjoon.agentdocs.access.domain.session.WebSession
import java.time.Duration

// The browser's sign-in token. Scripts cannot read it, and Lax keeps it on links into the app and the return from
// GitHub while leaving it off other sites' form posts. It is Secure whenever the public address is https.
class SessionCookie(
    private val secure: Boolean,
) {
    fun read(request: HttpServletRequest): String? =
        request.cookies
            ?.firstOrNull { it.name == NAME }
            ?.value
            ?.takeIf(String::isNotBlank)

    fun write(
        response: HttpServletResponse,
        token: String,
    ) = set(response, token, WebSession.idleLimit)

    fun clear(response: HttpServletResponse) = set(response, "", Duration.ZERO)

    private fun set(
        response: HttpServletResponse,
        value: String,
        maxAge: Duration,
    ) {
        val cookie =
            ResponseCookie
                .from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build()
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
    }

    private companion object {
        const val NAME = "agent_docs_session"
    }
}
