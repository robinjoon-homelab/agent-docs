package xyz.robinjoon.agentdocs.access.application.port.input

import xyz.robinjoon.agentdocs.access.application.model.ClientRegistration
import xyz.robinjoon.agentdocs.access.domain.client.OAuthClient

interface ClientRegistrationUseCase {
    // Fails with OAuthException invalid_redirect_uri or invalid_client_metadata.
    fun register(registration: ClientRegistration): OAuthClient
}
