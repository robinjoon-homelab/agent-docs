package xyz.robinjoon.agentdocs.access.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import xyz.robinjoon.agentdocs.access.application.port.output.WebSessionRepository
import xyz.robinjoon.agentdocs.access.domain.TokenHash
import xyz.robinjoon.agentdocs.access.domain.session.SessionTerm
import xyz.robinjoon.agentdocs.access.domain.session.WebSession
import xyz.robinjoon.agentdocs.access.domain.session.WebSessionId
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import java.time.Instant
import java.time.ZoneOffset

class ExposedWebSessionRepository : WebSessionRepository {
    override fun add(session: WebSession): WebSession {
        WebSessionsTable.insert {
            it[id] = session.id.value
            it[userId] = session.userId.value
            it[tokenHash] = session.tokenHash.value
            it[createdAt] = utc(session.term.startedAt)
            it[lastUsedAt] = utc(session.term.lastUsedAt)
        }
        return requireNotNull(findByTokenHash(session.tokenHash))
    }

    // Only the latest use changes; the person, token, and start stay as they were.
    override fun update(session: WebSession): WebSession {
        val updated = WebSessionsTable.update({ WebSessionsTable.id eq session.id.value }) { it[lastUsedAt] = utc(session.term.lastUsedAt) }
        check(updated == 1) { "Web session to update does not exist: ${session.id}" }
        return requireNotNull(findByTokenHash(session.tokenHash))
    }

    override fun findByTokenHash(hash: TokenHash): WebSession? =
        WebSessionsTable
            .selectAll()
            .where { WebSessionsTable.tokenHash eq hash.value }
            .singleOrNull()
            ?.toWebSession()

    override fun deleteByTokenHash(hash: TokenHash) {
        WebSessionsTable.deleteWhere { tokenHash eq hash.value }
    }

    override fun deleteUnusedBefore(cutoff: Instant) {
        WebSessionsTable.deleteWhere { lastUsedAt less utc(cutoff) }
    }

    private fun ResultRow.toWebSession(): WebSession =
        WebSession.restore(
            WebSessionId(this[WebSessionsTable.id]),
            UserId(this[WebSessionsTable.userId]),
            TokenHash(this[WebSessionsTable.tokenHash]),
            SessionTerm(this[WebSessionsTable.createdAt].toInstant(), this[WebSessionsTable.lastUsedAt].toInstant()),
        )

    private fun utc(instant: Instant) = instant.atOffset(ZoneOffset.UTC)
}
