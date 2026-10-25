package xyz.robinjoon.agentdocs.access.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.access.application.model.SessionCaller
import xyz.robinjoon.agentdocs.access.application.port.input.WebSessionUseCase
import xyz.robinjoon.agentdocs.access.application.port.output.SecretSource
import xyz.robinjoon.agentdocs.access.application.port.output.UserRepository
import xyz.robinjoon.agentdocs.access.application.port.output.WebSessionRepository
import xyz.robinjoon.agentdocs.access.domain.TokenHash
import xyz.robinjoon.agentdocs.access.domain.session.WebSession
import xyz.robinjoon.agentdocs.access.domain.session.WebSessionId
import xyz.robinjoon.agentdocs.access.domain.user.Allowlist
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import java.time.Clock
import java.util.UUID

// Runs on every browser request. The person and the allowlist are read each time rather than kept with the sign-in,
// so taking someone off the allowlist or changing the owner applies from their next request.
@Transactional(readOnly = true)
class WebSessionService(
    private val sessions: WebSessionRepository,
    private val users: UserRepository,
    private val allowlist: Allowlist,
    private val secrets: SecretSource,
    private val clock: Clock,
) : WebSessionUseCase {
    @Transactional
    override fun start(userId: UUID): String {
        val now = clock.instant()
        sessions.deleteUnusedBefore(now.minus(WebSession.idleLimit))
        val (token, hash) = Tokens.next(secrets, Tokens.WEB)
        sessions.add(WebSession.start(WebSessionId(UUID.randomUUID()), UserId(userId), hash, now))
        return token
    }

    @Transactional
    override fun callerFor(token: String): SessionCaller? {
        if (!token.startsWith(Tokens.WEB)) return null
        val now = clock.instant()
        val session = sessions.findByTokenHash(TokenHash.of(token))?.takeIf { it.acceptsAt(now) } ?: return null
        val user = users.findById(session.userId) ?: return null
        val role = allowlist.roleOf(user.account.login) ?: return null
        val renewed = session.used(now)?.let(sessions::update)
        return SessionCaller(Callers.person(user, role), renewed != null)
    }

    @Transactional
    override fun end(token: String) {
        if (token.startsWith(Tokens.WEB)) sessions.deleteByTokenHash(TokenHash.of(token))
    }
}
