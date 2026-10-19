package xyz.robinjoon.agentdocs.access.domain.connection

import xyz.robinjoon.agentdocs.access.domain.InvalidAccessException
import xyz.robinjoon.agentdocs.domain.AggregateRoot
import java.time.Duration
import java.time.Instant

// One client's standing permission to act for a person in some spaces, until it expires or is revoked. A revoked or
// narrowed connection applies from its next request, because every request is checked against the stored state.
// No access token outlives its connection.
@AggregateRoot
@ConsistentCopyVisibility
data class Connection private constructor(
    val id: ConnectionId,
    val owner: ConnectionOwner,
    val grant: Grant,
    val state: ConnectionState,
) {
    init {
        if (term.lifetime !in ConnectionTerm.shortestLifetime..ConnectionTerm.longestLifetime) {
            throw InvalidAccessException("a connection lasts 1 to ${ConnectionTerm.MAX_LIFETIME_DAYS} days")
        }
    }

    val term: ConnectionTerm get() = state.term

    val credentials: Credentials get() = state.credentials

    fun isActiveAt(at: Instant): Boolean = term.revokedAt == null && at < term.expiresAt

    fun acceptsAccessAt(at: Instant): Boolean = isActiveAt(at) && at < credentials.accessExpiresAt

    fun revoke(at: Instant): Connection? = if (term.revokedAt != null) null else copy(state = state.copy(term = term.copy(revokedAt = at)))

    fun regrant(grant: Grant): Connection = copy(grant = grant)

    // A refresh never extends the connection: the new access token still ends with it.
    fun rotate(
        credentials: Credentials,
        at: Instant,
    ): Connection {
        if (!isActiveAt(at)) {
            throw InvalidAccessException("an inactive connection cannot get new tokens")
        }
        return copy(state = ConnectionState(term.copy(lastUsedAt = at), bounded(credentials, term)))
    }

    // Use is recorded at most once a minute, so a busy agent does not turn every read into a write.
    fun used(at: Instant): Connection? {
        val last = term.lastUsedAt
        if (last != null && Duration.between(last, at) < useResolution) return null
        return copy(state = state.copy(term = term.copy(lastUsedAt = at)))
    }

    companion object {
        private val useResolution: Duration = Duration.ofMinutes(1)

        fun issue(
            id: ConnectionId,
            owner: ConnectionOwner,
            grant: Grant,
            state: ConnectionState,
        ): Connection = Connection(id, owner, grant, state.copy(credentials = bounded(state.credentials, state.term)))

        fun restore(
            id: ConnectionId,
            owner: ConnectionOwner,
            grant: Grant,
            state: ConnectionState,
        ): Connection = Connection(id, owner, grant, state)

        private fun bounded(
            credentials: Credentials,
            term: ConnectionTerm,
        ): Credentials = credentials.copy(accessExpiresAt = minOf(credentials.accessExpiresAt, term.expiresAt))
    }
}
