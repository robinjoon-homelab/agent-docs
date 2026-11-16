package xyz.robinjoon.agentdocs.access.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.access.application.model.Caller
import xyz.robinjoon.agentdocs.access.application.model.SessionCaller
import xyz.robinjoon.agentdocs.access.application.port.input.WebSessionUseCase
import xyz.robinjoon.agentdocs.access.application.port.output.SecretSource
import xyz.robinjoon.agentdocs.access.application.port.output.SpaceMembershipRepository
import xyz.robinjoon.agentdocs.access.application.port.output.UserRepository
import xyz.robinjoon.agentdocs.access.application.port.output.WebSessionRepository
import xyz.robinjoon.agentdocs.access.domain.TokenHash
import xyz.robinjoon.agentdocs.access.domain.session.WebSession
import xyz.robinjoon.agentdocs.access.domain.session.WebSessionId
import xyz.robinjoon.agentdocs.access.domain.user.Allowlist
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import xyz.robinjoon.agentdocs.access.domain.user.UserRole
import java.time.Clock
import java.time.Instant
import java.util.UUID

// Runs on every browser request. The person, the allowlist, and the person's joined memberships are read each time
// rather than kept with the sign-in, so taking someone off the allowlist, changing the owner, or changing or revoking a
// membership applies from their next request. A member whose last membership was revoked and who is not on the
// allowlist is no longer let in. Nothing here fixes an invitation; only a GitHub sign-in does.
@Transactional(readOnly = true)
class WebSessionService(
    private val sessions: WebSessionRepository,
    private val users: UserRepository,
    private val memberships: SpaceMembershipRepository,
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
        val now = clock.instant()
        val session = sessionFor(token, now) ?: return null
        val caller = callerOf(session) ?: return null
        val renewed = session.used(now)?.let(sessions::update)
        return SessionCaller(caller, renewed != null)
    }

    override fun currentCaller(token: String): Caller? = sessionFor(token, clock.instant())?.let(::callerOf)

    private fun sessionFor(
        token: String,
        now: Instant,
    ): WebSession? {
        if (!token.startsWith(Tokens.WEB)) return null
        return sessions.findByTokenHash(TokenHash.of(token))?.takeIf { it.acceptsAt(now) }
    }

    private fun callerOf(session: WebSession): Caller? {
        val user = users.findById(session.userId) ?: return null
        val role = allowlist.roleOf(user.account.login)
        if (role == UserRole.OWNER) return Callers.person(user, role)
        val joined = memberships.findActiveByMember(user.id)
        if (role == null && joined.isEmpty()) return null
        return Callers.person(user, UserRole.MEMBER, joined)
    }

    @Transactional
    override fun end(token: String) {
        if (token.startsWith(Tokens.WEB)) sessions.deleteByTokenHash(TokenHash.of(token))
    }
}
