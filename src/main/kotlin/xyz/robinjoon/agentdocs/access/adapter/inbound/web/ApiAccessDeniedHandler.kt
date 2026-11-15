package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.csrf.CsrfException

// Filter failures happen before controller advice, so they carry the same actionable problem fields here.
class ApiAccessDeniedHandler : AccessDeniedHandler {
    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AccessDeniedException,
    ) {
        val fix =
            if (exception is CsrfException) {
                "reload the page to obtain a current CSRF token and retry"
            } else {
                "sign in with an account permitted to access this resource"
            }
        response.status = HttpServletResponse.SC_FORBIDDEN
        response.contentType = "application/problem+json"
        response.writer.write("""{"title":"Forbidden","status":403,"code":"forbidden","fix":"$fix"}""")
    }
}
