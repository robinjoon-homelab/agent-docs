package xyz.robinjoon.agentdocs.access.domain.authorization

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.access.domain.TokenHash
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionScope
import xyz.robinjoon.agentdocs.access.domain.connection.Grant
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import java.time.Duration
import java.time.Instant
import java.util.UUID

class AuthorizationRequestTest {
    private val now = Instant.parse("2026-10-18T09:00:00Z")

    // challenge = base64url(SHA-256(verifier)), computed outside the code under test.
    private val verifier = "dBjftJeZ4CVP-mB92K1uhbHmnd17tLmcpf1Or0dfHq4"
    private val challenge = PkceChallenge("MpKhuRql5U_ItQvQquj4AyZQ-hTxEsV8TScgj9SGVsY")
    private val ask =
        AuthorizationAsk(
            "https://claude.ai/oauth/claude-code-client-metadata",
            "http://localhost:53682/callback",
            "xyz",
            challenge,
            "https://docs.example.com/mcp",
            null,
        )
    private val code = TokenHash.of("adt_c_code")
    private val approval =
        Approval(UserId(UUID.randomUUID()), Grant.of(setOf(UUID.randomUUID()), ConnectionScope.EDIT), Duration.ofDays(90), code)
    private val approved =
        AuthorizationRequest
            .start(
                AuthorizationRequestId(UUID.randomUUID()),
                ask,
                now,
            ).approve(approval, now.plusSeconds(30))

    @Test
    fun `a code is exchanged once with the matching client, redirect, and verifier`() {
        val (exchanged, granted) = approved.exchange(CodeExchange(ask.clientId, code, ask.redirectUri, verifier), now.plusSeconds(60))

        assertThat(granted).isEqualTo(approval)
        assertThatThrownBy { exchanged.exchange(CodeExchange(ask.clientId, code, ask.redirectUri, verifier), now.plusSeconds(61)) }
            .isInstanceOfSatisfying(AuthorizationFailedException::class.java) { assertThat(it.error).isEqualTo("invalid_grant") }
    }

    @Test
    fun `a wrong verifier, client, redirect, or an expired code is invalid_grant`() {
        val attempts =
            listOf(
                CodeExchange(ask.clientId, code, ask.redirectUri, "x".repeat(43)) to now.plusSeconds(60),
                CodeExchange("https://chatgpt.com/oauth/codex/client.json", code, ask.redirectUri, verifier) to now.plusSeconds(60),
                CodeExchange(ask.clientId, code, "http://localhost:1/callback", verifier) to now.plusSeconds(60),
                CodeExchange(ask.clientId, code, ask.redirectUri, verifier) to now.plus(Duration.ofMinutes(11)),
            )

        attempts.forEach { (exchange, at) ->
            assertThatThrownBy { approved.exchange(exchange, at) }
                .isInstanceOfSatisfying(AuthorizationFailedException::class.java) { assertThat(it.error).isEqualTo("invalid_grant") }
        }
    }

    @Test
    fun `a request waits ten minutes for the owner and is decided once`() {
        val pending = AuthorizationRequest.start(AuthorizationRequestId(UUID.randomUUID()), ask, now)

        assertThat(pending.isOpenAt(now.plus(Duration.ofMinutes(9)))).isTrue()
        assertThat(pending.isOpenAt(now.plus(Duration.ofMinutes(10)))).isFalse()
        assertThatThrownBy { pending.deny(now).approve(approval, now) }.isInstanceOf(AuthorizationFailedException::class.java)
    }
}
