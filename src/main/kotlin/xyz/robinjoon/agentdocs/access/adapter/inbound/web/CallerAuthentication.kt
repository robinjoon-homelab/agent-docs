package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import xyz.robinjoon.agentdocs.access.application.model.Caller

// The authentication every request carries once it is known who is calling: a signed-in person from the session or an
// agent from its bearer token. Other contexts read the Caller from it and decide permissions themselves; only the
// operational endpoints, such as /actuator/metrics, check ROLE_OWNER.
class CallerAuthentication(
    private val caller: Caller,
) : AbstractAuthenticationToken(authoritiesOf(caller)) {
    init {
        isAuthenticated = true
    }

    override fun getPrincipal(): Caller = caller

    override fun getCredentials(): Any? = null

    override fun getName(): String = caller.login

    private companion object {
        fun authoritiesOf(caller: Caller): List<SimpleGrantedAuthority> =
            listOfNotNull(
                SimpleGrantedAuthority(if (caller.agent == null) "ROLE_PERSON" else "ROLE_AGENT"),
                SimpleGrantedAuthority("ROLE_OWNER").takeIf { caller.owner },
            )
    }
}
