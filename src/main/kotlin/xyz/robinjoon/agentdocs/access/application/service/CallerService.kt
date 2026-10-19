package xyz.robinjoon.agentdocs.access.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.access.application.model.Caller
import xyz.robinjoon.agentdocs.access.application.port.input.CallerUseCase
import xyz.robinjoon.agentdocs.access.application.port.output.ConnectionRepository
import xyz.robinjoon.agentdocs.access.application.port.output.UserRepository
import xyz.robinjoon.agentdocs.access.domain.TokenHash
import java.time.Clock

// Runs on every agent request, so the stored connection, not a cached copy, decides: a revoked or narrowed connection
// takes effect immediately.
@Transactional(readOnly = true)
class CallerService(
    private val connections: ConnectionRepository,
    private val users: UserRepository,
    private val clock: Clock,
) : CallerUseCase {
    @Transactional
    override fun callerFor(token: String): Caller? {
        if (!token.startsWith(Tokens.ACCESS) && !token.startsWith(Tokens.SCRIPT)) return null
        val now = clock.instant()
        val connection = connections.findByAccessHash(TokenHash.of(token))?.takeIf { it.acceptsAccessAt(now) } ?: return null
        val user = users.findById(connection.owner.userId) ?: return null
        connection.used(now)?.let(connections::update)
        return Callers.agent(connection, user.account.login)
    }
}
