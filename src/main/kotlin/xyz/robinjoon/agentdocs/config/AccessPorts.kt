package xyz.robinjoon.agentdocs.config

import xyz.robinjoon.agentdocs.access.adapter.outbound.crypto.SecureRandomSecretSource
import xyz.robinjoon.agentdocs.access.adapter.outbound.persistence.ExposedAuthorizationRequestRepository
import xyz.robinjoon.agentdocs.access.adapter.outbound.persistence.ExposedConnectionRepository
import xyz.robinjoon.agentdocs.access.adapter.outbound.persistence.ExposedEmergencyLoginRepository
import xyz.robinjoon.agentdocs.access.adapter.outbound.persistence.ExposedOAuthClientRepository
import xyz.robinjoon.agentdocs.access.adapter.outbound.persistence.ExposedSpaceMembershipRepository
import xyz.robinjoon.agentdocs.access.adapter.outbound.persistence.ExposedUserRepository
import xyz.robinjoon.agentdocs.access.adapter.outbound.persistence.ExposedWebSessionRepository
import xyz.robinjoon.agentdocs.access.application.port.output.AuthorizationRequestRepository
import xyz.robinjoon.agentdocs.access.application.port.output.ConnectionRepository
import xyz.robinjoon.agentdocs.access.application.port.output.EmergencyLoginRepository
import xyz.robinjoon.agentdocs.access.application.port.output.OAuthClientRepository
import xyz.robinjoon.agentdocs.access.application.port.output.SecretSource
import xyz.robinjoon.agentdocs.access.application.port.output.SpaceMembershipRepository
import xyz.robinjoon.agentdocs.access.application.port.output.UserRepository
import xyz.robinjoon.agentdocs.access.application.port.output.WebSessionRepository

// The access context's output ports, shared by its use cases.
class AccessPorts {
    val emergency: EmergencyLoginRepository = ExposedEmergencyLoginRepository()
    val users: UserRepository = ExposedUserRepository()
    val connections: ConnectionRepository = ExposedConnectionRepository()
    val clients: OAuthClientRepository = ExposedOAuthClientRepository()
    val requests: AuthorizationRequestRepository = ExposedAuthorizationRequestRepository()
    val sessions: WebSessionRepository = ExposedWebSessionRepository()
    val memberships: SpaceMembershipRepository = ExposedSpaceMembershipRepository()
    val secrets: SecretSource = SecureRandomSecretSource()
}
