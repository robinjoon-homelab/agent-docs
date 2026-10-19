package xyz.robinjoon.agentdocs.access.domain.connection

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.access.domain.InvalidAccessException
import xyz.robinjoon.agentdocs.access.domain.TokenHash
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import java.time.Duration
import java.time.Instant
import java.util.UUID

class ConnectionTest {
    private val now = Instant.parse("2026-10-18T09:00:00Z")
    private val owner =
        ConnectionOwner(
            UserId(UUID.randomUUID()),
            ConnectionKind.AGENT,
            ClientRef("https://claude.ai/oauth/claude-code-client-metadata", "Claude Code", "claude.ai"),
        )
    private val term = ConnectionTerm(now, now.plus(Duration.ofDays(90)), null, null)

    @Test
    fun `no access token outlives its connection, at issue or after a refresh`() {
        val issued = connection(accessExpiresAt = now.plus(Duration.ofDays(100)))
        val nearEnd = now.plus(Duration.ofDays(90)).minusSeconds(60)
        val rotated = issued.rotate(Credentials(TokenHash.of("next"), nearEnd.plus(Duration.ofHours(1)), TokenHash.of("refresh")), nearEnd)

        assertThat(issued.credentials.accessExpiresAt).isEqualTo(term.expiresAt)
        assertThat(rotated.credentials.accessExpiresAt).isEqualTo(term.expiresAt)
        assertThat(rotated.term.lastUsedAt).isEqualTo(nearEnd)
    }

    @Test
    fun `a revoked or expired connection accepts nothing and cannot refresh`() {
        val connection = connection(accessExpiresAt = now.plus(Duration.ofHours(1)))
        val revoked = requireNotNull(connection.revoke(now.plusSeconds(1)))

        assertThat(connection.acceptsAccessAt(now.plusSeconds(10))).isTrue()
        assertThat(connection.acceptsAccessAt(now.plus(Duration.ofHours(2)))).isFalse()
        assertThat(revoked.acceptsAccessAt(now.plusSeconds(10))).isFalse()
        assertThat(revoked.revoke(now)).isNull()
        assertThatThrownBy { revoked.rotate(connection.credentials, now.plusSeconds(10)) }.isInstanceOf(InvalidAccessException::class.java)
    }

    @Test
    fun `use is recorded at most once a minute`() {
        val used = requireNotNull(connection(now.plusSeconds(3_600)).used(now))

        assertThat(used.used(now.plusSeconds(30))).isNull()
        assertThat(used.used(now.plusSeconds(61))?.term?.lastUsedAt).isEqualTo(now.plusSeconds(61))
    }

    @Test
    fun `a connection lasts one to 365 days`() {
        val tooLong = ConnectionTerm(now, now.plus(Duration.ofDays(366)), null, null)

        assertThatThrownBy {
            Connection.issue(
                ConnectionId(UUID.randomUUID()),
                owner,
                Grant.of(emptySet(), ConnectionScope.READ),
                ConnectionState(tooLong, credentials()),
            )
        }.isInstanceOf(InvalidAccessException::class.java)
        assertThatThrownBy { ConnectionTerm.lifetimeOf(0) }.isInstanceOf(InvalidAccessException::class.java)
        assertThat(ConnectionTerm.lifetimeOf(365)).isEqualTo(Duration.ofDays(365))
    }

    private fun credentials(): Credentials = Credentials(TokenHash.of("access"), now.plusSeconds(3_600), TokenHash.of("refresh"))

    private fun connection(accessExpiresAt: Instant): Connection =
        Connection.issue(
            ConnectionId(UUID.randomUUID()),
            owner,
            Grant.of(setOf(UUID.randomUUID()), ConnectionScope.COMMENT),
            ConnectionState(term, Credentials(TokenHash.of("access"), accessExpiresAt, TokenHash.of("refresh"))),
        )
}
