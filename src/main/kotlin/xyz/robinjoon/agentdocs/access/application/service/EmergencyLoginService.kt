package xyz.robinjoon.agentdocs.access.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.access.application.model.AccessSettings
import xyz.robinjoon.agentdocs.access.application.port.input.EmergencyLoginIssueUseCase
import xyz.robinjoon.agentdocs.access.application.port.input.EmergencyLoginUseCase
import xyz.robinjoon.agentdocs.access.application.port.input.WebSessionUseCase
import xyz.robinjoon.agentdocs.access.application.port.output.EmergencyLoginRepository
import xyz.robinjoon.agentdocs.access.application.port.output.SecretSource
import xyz.robinjoon.agentdocs.access.application.port.output.UserRepository
import xyz.robinjoon.agentdocs.access.domain.TokenHash
import xyz.robinjoon.agentdocs.access.domain.emergency.EmergencyLoginGrant
import java.time.Clock

@Transactional(readOnly = true)
class EmergencyLoginService(
    private val grants: EmergencyLoginRepository,
    private val users: UserRepository,
    private val secrets: SecretSource,
    private val sessions: WebSessionUseCase,
    private val settings: AccessSettings,
    private val clock: Clock,
) : EmergencyLoginIssueUseCase,
    EmergencyLoginUseCase {
    @Transactional
    override fun issue(): String {
        val owner = users.findByLogin(settings.owner.trim()) ?: error("The current owner must sign in with GitHub first")
        check(owner.account.login.equals(settings.owner.trim(), ignoreCase = true)) { "The current owner is required" }
        val token = "ade_" + secrets.next()
        grants.add(EmergencyLoginGrant.issue(TokenHash.of(token), owner.id, clock.instant()))
        return settings.issuer.trimEnd('/') + "/emergency-login#" + token
    }

    @Transactional
    override fun consume(token: String): String {
        require(token.matches(Regex("ade_[A-Za-z0-9_-]{43}"))) { "Emergency login is unavailable" }
        val grant = requireNotNull(grants.lock(TokenHash.of(token))) { "Emergency login is unavailable" }
        val consumed = grant.consume(clock.instant())
        val user = requireNotNull(users.findById(grant.userId)) { "Emergency login is unavailable" }
        require(user.account.login.equals(settings.owner.trim(), ignoreCase = true)) { "Emergency login is unavailable" }
        grants.update(consumed)
        return sessions.start(user.id.value)
    }
}
