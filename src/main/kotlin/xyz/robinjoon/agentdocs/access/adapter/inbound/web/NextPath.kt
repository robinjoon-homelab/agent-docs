package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import jakarta.servlet.http.HttpServletRequest

// Where to go after GitHub sign-in. The web app passes ?next= to /oauth2/authorization/github; only a path on this
// server is accepted, so the parameter cannot send the browser elsewhere.
internal object NextPath {
    private const val ATTRIBUTE = "agent-docs.next"

    fun remember(request: HttpServletRequest) {
        val next = request.getParameter("next") ?: return
        if (isLocal(next)) request.getSession(true).setAttribute(ATTRIBUTE, next)
    }

    fun take(request: HttpServletRequest): String? {
        val session = request.getSession(false) ?: return null
        val next = session.getAttribute(ATTRIBUTE) as? String
        session.removeAttribute(ATTRIBUTE)
        return next?.takeIf(::isLocal)
    }

    private fun isLocal(path: String): Boolean =
        path.startsWith("/") && !path.startsWith("//") && !path.startsWith("/\\") && path.none(Char::isISOControl)
}
