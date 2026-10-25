package xyz.robinjoon.agentdocs.access.domain.session

import xyz.robinjoon.agentdocs.access.domain.TokenHash
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import xyz.robinjoon.agentdocs.domain.AggregateRoot
import java.time.Duration
import java.time.Instant

// One browser's sign-in. It lasts while it is used and ends after 30 days without use, so a person who comes back
// regularly never signs in again. Only the cookie token's hash is kept.
@AggregateRoot
@ConsistentCopyVisibility
data class WebSession private constructor(
    val id: WebSessionId,
    val userId: UserId,
    val tokenHash: TokenHash,
    val term: SessionTerm,
) {
    fun acceptsAt(at: Instant): Boolean = at < term.lastUsedAt.plus(idleLimit)

    // Use is recorded at most once a day, so browsing does not turn every request into a write.
    fun used(at: Instant): WebSession? =
        if (Duration.between(term.lastUsedAt, at) < useResolution) null else copy(term = term.copy(lastUsedAt = at))

    companion object {
        val idleLimit: Duration = Duration.ofDays(30)
        private val useResolution: Duration = Duration.ofDays(1)

        fun start(
            id: WebSessionId,
            userId: UserId,
            tokenHash: TokenHash,
            at: Instant,
        ): WebSession = WebSession(id, userId, tokenHash, SessionTerm(at, at))

        fun restore(
            id: WebSessionId,
            userId: UserId,
            tokenHash: TokenHash,
            term: SessionTerm,
        ): WebSession = WebSession(id, userId, tokenHash, term)
    }
}
