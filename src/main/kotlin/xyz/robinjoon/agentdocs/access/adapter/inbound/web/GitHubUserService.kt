package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.user.OAuth2User
import xyz.robinjoon.agentdocs.access.application.model.GitHubIdentity
import xyz.robinjoon.agentdocs.access.application.port.input.SignInRefusedException
import xyz.robinjoon.agentdocs.access.application.port.input.SignInUseCase

// Reads who signed in from GitHub's user endpoint and lets the access context decide. The GitHub access token is used
// for that one call and dropped with the request.
class GitHubUserService(
    private val signIn: SignInUseCase,
    private val delegate: OAuth2UserService<OAuth2UserRequest, OAuth2User> = DefaultOAuth2UserService(),
) : OAuth2UserService<OAuth2UserRequest, OAuth2User> {
    override fun loadUser(request: OAuth2UserRequest): OAuth2User {
        val user =
            delegate.loadUser(request) ?: throw OAuth2AuthenticationException(OAuth2Error("invalid_user", "GitHub returned no user", null))
        val id = (user.attributes["id"] as? Number)?.toLong()
        val login = user.attributes["login"] as? String
        if (id == null ||
            login == null
        ) {
            throw OAuth2AuthenticationException(OAuth2Error("invalid_user", "GitHub did not report an id and login", null))
        }
        val identity = GitHubIdentity(id, login, user.attributes["name"] as? String, user.attributes["avatar_url"] as? String)
        val caller =
            try {
                signIn.signIn(identity)
            } catch (exception: SignInRefusedException) {
                throw OAuth2AuthenticationException(OAuth2Error(NOT_ALLOWED, exception.message, null), exception)
            }
        return CallerUser(caller, user.attributes)
    }

    companion object {
        const val NOT_ALLOWED = "not_allowed"
    }
}
