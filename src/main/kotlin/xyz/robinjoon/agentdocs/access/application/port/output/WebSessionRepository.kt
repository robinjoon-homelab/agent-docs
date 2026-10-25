package xyz.robinjoon.agentdocs.access.application.port.output

import xyz.robinjoon.agentdocs.access.domain.TokenHash
import xyz.robinjoon.agentdocs.access.domain.session.WebSession
import java.time.Instant

interface WebSessionRepository {
    fun add(session: WebSession): WebSession

    fun update(session: WebSession): WebSession

    fun findByTokenHash(hash: TokenHash): WebSession?

    fun deleteByTokenHash(hash: TokenHash)

    fun deleteUnusedBefore(cutoff: Instant)
}
