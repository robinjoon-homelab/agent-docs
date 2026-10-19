package xyz.robinjoon.agentdocs.access.application

import xyz.robinjoon.agentdocs.access.application.model.AccessSettings
import xyz.robinjoon.agentdocs.access.application.model.Caller
import xyz.robinjoon.agentdocs.access.application.model.CallerAgent
import xyz.robinjoon.agentdocs.access.application.model.CallerScope
import xyz.robinjoon.agentdocs.access.domain.TokenHash
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
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

object AccessFixtures {
    val now: Instant = Instant.parse("2026-10-18T09:00:00Z")
    val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    val ownerId: UUID = UUID.fromString("11111111-1111-4111-8111-111111111111")
    val space: UUID = UUID.fromString("33333333-3333-4333-8333-333333333333")
    val settings = AccessSettings("robinjoon", emptySet(), "https://docs.example.com")
    val owner = Caller(ownerId, "robinjoon", true, null, null, CallerScope.OWNER)
    val agentCaller = Caller(ownerId, "robinjoon", false, CallerAgent(UUID.randomUUID(), "Claude Code"), setOf(space), CallerScope.EDIT)
    const val CLAUDE_CODE = "https://claude.ai/oauth/claude-code-client-metadata"
    val claudeCode =
        OAuthClient.register(
            CLAUDE_CODE,
            ClientKind.METADATA_DOCUMENT,
            ClientMetadata.of("Claude Code", listOf("http://localhost/callback")),
            now,
        )

    fun connection(
        accessToken: String,
        refreshToken: String?,
    ): Connection {
        val owner = ConnectionOwner(UserId(ownerId), ConnectionKind.AGENT, ClientRef(CLAUDE_CODE, "Claude Code", "claude.ai"))
        val term = ConnectionTerm(now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(89)), null, null)
        val credentials = Credentials(TokenHash.of(accessToken), now.plus(Duration.ofHours(1)), refreshToken?.let(TokenHash::of))
        return Connection.restore(
            ConnectionId(UUID.randomUUID()),
            owner,
            Grant.of(setOf(space), ConnectionScope.EDIT),
            ConnectionState(term, credentials),
        )
    }
}
