package xyz.robinjoon.agentdocs.access.application.service

import xyz.robinjoon.agentdocs.access.application.model.AccessSettings
import xyz.robinjoon.agentdocs.access.application.model.OAuthException
import xyz.robinjoon.agentdocs.access.application.port.output.UserRepository
import xyz.robinjoon.agentdocs.access.domain.user.Allowlist
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import xyz.robinjoon.agentdocs.access.domain.user.UserRole

// Token issuance rechecks the delegator's current role, just as each authenticated request does.
class TokenPolicy(
    private val users: UserRepository,
    private val settings: AccessSettings,
) {
    private val allowlist = Allowlist(settings.owner, settings.members)

    fun requireOwner(userId: UserId) {
        val user = users.findById(userId)
        if (user == null || allowlist.roleOf(user.account.login) != UserRole.OWNER) {
            throw OAuthException("invalid_grant", "the delegator no longer has space access")
        }
    }

    fun requireResource(resources: List<String>) {
        if (resources.any { it.trimEnd('/') != settings.resource }) {
            throw OAuthException("invalid_target", "resource must be ${settings.resource}")
        }
    }
}
