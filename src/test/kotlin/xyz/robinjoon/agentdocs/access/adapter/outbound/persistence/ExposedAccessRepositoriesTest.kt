package xyz.robinjoon.agentdocs.access.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.access.domain.TokenHash
import xyz.robinjoon.agentdocs.access.domain.authorization.Approval
import xyz.robinjoon.agentdocs.access.domain.authorization.AuthorizationAsk
import xyz.robinjoon.agentdocs.access.domain.authorization.AuthorizationRequest
import xyz.robinjoon.agentdocs.access.domain.authorization.AuthorizationRequestId
import xyz.robinjoon.agentdocs.access.domain.authorization.PkceChallenge
import xyz.robinjoon.agentdocs.access.domain.client.ClientKind
import xyz.robinjoon.agentdocs.access.domain.client.ClientMetadata
import xyz.robinjoon.agentdocs.access.domain.client.OAuthClient
import xyz.robinjoon.agentdocs.access.domain.connection.ClientRef
import xyz.robinjoon.agentdocs.access.domain.connection.Connection
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionId
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionKind
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionOwner
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionScope
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionState
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionTerm
import xyz.robinjoon.agentdocs.access.domain.connection.Credentials
import xyz.robinjoon.agentdocs.access.domain.connection.Grant
import xyz.robinjoon.agentdocs.access.domain.session.WebSession
import xyz.robinjoon.agentdocs.access.domain.session.WebSessionId
import xyz.robinjoon.agentdocs.access.domain.user.GitHubAccount
import xyz.robinjoon.agentdocs.access.domain.user.User
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import xyz.robinjoon.agentdocs.access.domain.user.UserProfile
import xyz.robinjoon.agentdocs.support.persistence.MigratedH2Database
import java.time.Duration
import java.time.Instant
import java.util.UUID

class ExposedAccessRepositoriesTest {
    private val database = MigratedH2Database()
    private val users = ExposedUserRepository()
    private val connections = ExposedConnectionRepository()
    private val clients = ExposedOAuthClientRepository()
    private val requests = ExposedAuthorizationRequestRepository()
    private val sessions = ExposedWebSessionRepository()
    private val at = Instant.parse("2026-10-18T09:00:00.123456Z")
    private val user =
        User.register(
            UserId(UUID.randomUUID()),
            GitHubAccount(42, "robinjoon"),
            UserProfile("Robin", "https://avatars.example/42"),
            at,
        )

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `a user is found by GitHub id and keeps a renamed login`() {
        database.inTransaction { users.add(user) }
        database.inTransaction { users.update(user.signedIn(GitHubAccount(42, "robin-joon"), user.profile, at.plusSeconds(1))) }

        assertThat(database.inTransaction { users.findByGithubId(42) }?.account?.login).isEqualTo("robin-joon")
        assertThat(database.inTransaction { users.findByGithubId(7) }).isNull()
    }

    @Test
    fun `a connection round trips with its spaces and is found by either token hash`() {
        database.inTransaction { users.add(user) }
        val connection = connection()
        database.inTransaction { connections.add(connection) }

        assertThat(database.inTransaction { connections.findByAccessHash(TokenHash.of("access")) }).isEqualTo(connection)
        assertThat(database.inTransaction { connections.findByRefreshHashForUpdate(TokenHash.of("refresh")) }?.id).isEqualTo(connection.id)
        val narrowed = database.inTransaction { connections.update(connection.regrant(Grant.of(emptySet(), ConnectionScope.READ))) }
        assertThat(narrowed.grant.spaces).isEmpty()
        assertThat(database.inTransaction { connections.findByUser(user.id) }.map { it.id }).containsExactly(connection.id)
    }

    @Test
    fun `a client is saved again when its metadata document changes`() {
        val client =
            OAuthClient.register(
                "https://claude.ai/oauth/claude-code-client-metadata",
                ClientKind.METADATA_DOCUMENT,
                ClientMetadata.of("Claude Code", listOf("http://localhost/callback")),
                at,
            )
        database.inTransaction { clients.save(client) }

        val refreshed =
            database.inTransaction {
                clients.save(
                    client.refreshed(ClientMetadata.of("Claude Code", listOf("http://localhost/callback", "http://127.0.0.1/callback"))),
                )
            }

        assertThat(refreshed.metadata.redirectUris).containsExactly("http://localhost/callback", "http://127.0.0.1/callback")
        assertThat(database.inTransaction { clients.find("unknown") }).isNull()
    }

    @Test
    fun `an approved request is found by its code and stays findable after the exchange`() {
        val ask =
            AuthorizationAsk(
                "client",
                "http://localhost/callback",
                null,
                PkceChallenge("MpKhuRql5U_ItQvQquj4AyZQ-hTxEsV8TScgj9SGVsY"),
                "https://docs.example.com/mcp",
                ConnectionScope.READ,
            )
        val request = AuthorizationRequest.start(AuthorizationRequestId(UUID.randomUUID()), ask, at)
        val approval =
            Approval(user.id, Grant.of(setOf(UUID.randomUUID()), ConnectionScope.EDIT), Duration.ofDays(30), TokenHash.of("code"))
        database.inTransaction { requests.add(request) }

        val approved = database.inTransaction { requests.update(request.approve(approval, at.plusSeconds(10))) }

        assertThat(approved).isEqualTo(request.approve(approval, at.plusSeconds(10)))
        assertThat(database.inTransaction { requests.findByCodeForUpdate(TokenHash.of("code")) }?.id).isEqualTo(request.id)
    }

    @Test
    fun `a web sign-in is found by its token hash, keeps its latest use, and is gone once ended or unused`() {
        database.inTransaction { users.add(user) }
        val kept = WebSession.start(WebSessionId(UUID.randomUUID()), user.id, TokenHash.of("adt_w_kept"), at)
        val ended = WebSession.start(WebSessionId(UUID.randomUUID()), user.id, TokenHash.of("adt_w_ended"), at)
        val unused = WebSession.start(WebSessionId(UUID.randomUUID()), user.id, TokenHash.of("adt_w_unused"), at.minus(Duration.ofDays(31)))
        database.inTransaction { listOf(kept, ended, unused).forEach(sessions::add) }

        val renewed = requireNotNull(kept.used(at.plus(Duration.ofDays(2))))
        database.inTransaction { sessions.update(renewed) }
        database.inTransaction { sessions.deleteByTokenHash(TokenHash.of("adt_w_ended")) }
        database.inTransaction { sessions.deleteUnusedBefore(at.minus(Duration.ofDays(30))) }

        assertThat(database.inTransaction { sessions.findByTokenHash(TokenHash.of("adt_w_kept")) }).isEqualTo(renewed)
        assertThat(database.inTransaction { sessions.findByTokenHash(TokenHash.of("adt_w_ended")) }).isNull()
        assertThat(database.inTransaction { sessions.findByTokenHash(TokenHash.of("adt_w_unused")) }).isNull()
    }

    private fun connection(): Connection {
        val owner =
            ConnectionOwner(
                user.id,
                ConnectionKind.AGENT,
                ClientRef("https://claude.ai/oauth/claude-code-client-metadata", "Claude Code", "claude.ai"),
            )
        val term = ConnectionTerm(at, at.plus(Duration.ofDays(90)), null, null)
        val credentials = Credentials(TokenHash.of("access"), at.plus(Duration.ofHours(1)), TokenHash.of("refresh"))
        return Connection.issue(
            ConnectionId(UUID.randomUUID()),
            owner,
            Grant.of(setOf(UUID.randomUUID(), UUID.randomUUID()), ConnectionScope.COMMENT),
            ConnectionState(term, credentials),
        )
    }
}
