package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.user.OAuth2User
import xyz.robinjoon.agentdocs.access.application.model.Caller
import java.io.Serializable

// The GitHub login result once the allowlist accepted it. Only lives until SignInSuccessHandler replaces it with a
// CallerAuthentication.
class CallerUser(
    val caller: Caller,
    attributes: Map<String, Any>,
) : OAuth2User,
    Serializable {
    private val attributes: Map<String, Any> = attributes.toMap()

    override fun getName(): String = caller.login

    override fun getAttributes(): Map<String, Any> = attributes

    override fun getAuthorities(): Collection<GrantedAuthority> = listOf(SimpleGrantedAuthority("ROLE_PERSON"))
}
