package xyz.robinjoon.agentdocs.access.application

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.CLAUDE_CODE
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.claudeCode
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.clock
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.connection
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.now
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.ownerId
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.settings
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.space
import xyz.robinjoon.agentdocs.access.application.model.CodeGrant
import xyz.robinjoon.agentdocs.access.application.model.OAuthException
import xyz.robinjoon.agentdocs.access.application.model.RefreshGrant
import xyz.robinjoon.agentdocs.access.application.port.output.AuthorizationRequestRepository
import xyz.robinjoon.agentdocs.access.application.port.output.ConnectionRepository
import xyz.robinjoon.agentdocs.access.application.port.output.OAuthClientRepository
import xyz.robinjoon.agentdocs.access.application.port.output.SecretSource
import xyz.robinjoon.agentdocs.access.application.service.AccessJournal
import xyz.robinjoon.agentdocs.access.application.service.TokenService
import xyz.robinjoon.agentdocs.access.domain.TokenHash
import xyz.robinjoon.agentdocs.access.domain.authorization.Approval
import xyz.robinjoon.agentdocs.access.domain.authorization.AuthorizationAsk
import xyz.robinjoon.agentdocs.access.domain.authorization.AuthorizationDecision
import xyz.robinjoon.agentdocs.access.domain.authorization.AuthorizationRequest
import xyz.robinjoon.agentdocs.access.domain.authorization.AuthorizationRequestId
import xyz.robinjoon.agentdocs.access.domain.authorization.PkceChallenge
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionScope
import xyz.robinjoon.agentdocs.access.domain.connection.Grant
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import java.time.Duration
import java.util.UUID

class TokenServiceTest {
    private val requests = mockk<AuthorizationRequestRepository>()
    private val connections = mockk<ConnectionRepository>()
    private val clients = mockk<OAuthClientRepository>()
    private val secrets = mockk<SecretSource>()
    private val service = TokenService(requests, connections, clients, secrets, AccessJournal(mockk(), clock), settings)
    private val verifier = "dBjftJeZ4CVP-mB92K1uhbHmnd17tLmcpf1Or0dfHq4"
    private val redirect = "http://localhost:50111/callback"

    @Test
    fun `a code becomes a connection with the approved grant, an hour-long access token, and a refresh token`() {
        val approved = approvedRequest()
        every { requests.findByCodeForUpdate(TokenHash.of("adt_c_code")) } returns approved
        every { requests.update(any()) } answers { firstArg() }
        every { clients.find(CLAUDE_CODE) } returns claudeCode
        every { secrets.next() } returnsMany listOf("access", "refresh")
        every { connections.add(any()) } answers { firstArg() }

        val tokens = service.exchange(CodeGrant(CLAUDE_CODE, "adt_c_code", verifier, redirect, listOf("https://docs.example.com/mcp/")))

        assertThat(tokens.accessToken).isEqualTo("adt_a_access")
        assertThat(tokens.refreshToken).isEqualTo("adt_r_refresh")
        assertThat(tokens.expiresIn).isEqualTo(3_600)
        assertThat(tokens.scope).isEqualTo("comment")
    }

    @Test
    fun `a token for another resource is refused before any code is looked at`() {
        assertThatThrownBy {
            service.exchange(
                CodeGrant(CLAUDE_CODE, "adt_c_code", verifier, redirect, listOf("https://other.example/mcp")),
            )
        }.isInstanceOfSatisfying(OAuthException::class.java) { assertThat(it.error).isEqualTo("invalid_target") }
        assertThatThrownBy { service.exchange(CodeGrant(null, "adt_c_code", verifier, redirect, emptyList())) }
            .isInstanceOfSatisfying(OAuthException::class.java) { assertThat(it.error).isEqualTo("invalid_request") }
    }

    @Test
    fun `refreshing rotates both tokens and an unknown refresh token is invalid_grant`() {
        val active = connection("adt_a_old", "adt_r_old")
        every { connections.findByRefreshHashForUpdate(TokenHash.of("adt_r_old")) } returns active
        every { connections.findByRefreshHashForUpdate(TokenHash.of("adt_r_unknown")) } returns null
        every { secrets.next() } returnsMany listOf("new-access", "new-refresh")
        every { connections.update(any()) } answers { firstArg() }

        val tokens = service.refresh(RefreshGrant(CLAUDE_CODE, "adt_r_old", emptyList()))

        assertThat(tokens.accessToken).isEqualTo("adt_a_new-access")
        assertThat(tokens.refreshToken).isEqualTo("adt_r_new-refresh")
        assertThatThrownBy { service.refresh(RefreshGrant(CLAUDE_CODE, "adt_r_unknown", emptyList())) }
            .isInstanceOfSatisfying(OAuthException::class.java) { assertThat(it.error).isEqualTo("invalid_grant") }
    }

    private fun approvedRequest(): AuthorizationRequest {
        val ask =
            AuthorizationAsk(
                CLAUDE_CODE,
                redirect,
                "xyz",
                PkceChallenge("MpKhuRql5U_ItQvQquj4AyZQ-hTxEsV8TScgj9SGVsY"),
                settings.resource,
                null,
            )
        val approval =
            Approval(UserId(ownerId), Grant.of(setOf(space), ConnectionScope.COMMENT), Duration.ofDays(90), TokenHash.of("adt_c_code"))
        return AuthorizationRequest.restore(
            AuthorizationRequestId(UUID.randomUUID()),
            ask,
            AuthorizationDecision.Approved(approval),
            now.plusSeconds(300),
        )
    }
}
