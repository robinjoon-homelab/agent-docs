package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import org.springframework.security.core.Authentication
import xyz.robinjoon.agentdocs.access.application.model.Caller
import java.security.Principal

// Spring MVC resolves java.security.Principal from the request, which works the same with the security filters in
// place and in a standalone MockMvc test that passes one.
internal object Callers {
    fun of(principal: Principal?): Caller = ((principal as? Authentication)?.principal as? Caller) ?: throw UnauthenticatedException()
}
