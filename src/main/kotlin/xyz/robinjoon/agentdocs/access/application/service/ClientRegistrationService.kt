package xyz.robinjoon.agentdocs.access.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.access.application.model.ClientRegistration
import xyz.robinjoon.agentdocs.access.application.port.input.ClientRegistrationUseCase
import xyz.robinjoon.agentdocs.access.domain.client.OAuthClient

// Dynamic client registration stays for clients without a metadata document. Registering grants nothing by itself:
// the owner still approves every connection.
@Transactional(readOnly = true)
class ClientRegistrationService(
    private val directory: ClientDirectory,
) : ClientRegistrationUseCase {
    @Transactional
    override fun register(registration: ClientRegistration): OAuthClient = directory.register(registration)
}
