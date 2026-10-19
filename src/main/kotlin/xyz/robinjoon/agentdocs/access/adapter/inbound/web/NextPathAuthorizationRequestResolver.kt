package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest

// Remembers ?next= when sign-in starts, then lets Spring build the GitHub authorization request as usual.
class NextPathAuthorizationRequestResolver(
    registrations: ClientRegistrationRepository,
) : OAuth2AuthorizationRequestResolver {
    private val delegate = DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization")

    override fun resolve(request: HttpServletRequest): OAuth2AuthorizationRequest? =
        delegate.resolve(request)?.also {
            NextPath.remember(request)
        }

    override fun resolve(
        request: HttpServletRequest,
        clientRegistrationId: String,
    ): OAuth2AuthorizationRequest? = delegate.resolve(request, clientRegistrationId)?.also { NextPath.remember(request) }
}
