package xyz.robinjoon.agentdocs.access.application

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.access.application.model.AccessSettings
import xyz.robinjoon.agentdocs.access.application.port.input.WebSessionUseCase
import xyz.robinjoon.agentdocs.access.application.port.output.EmergencyLoginRepository
import xyz.robinjoon.agentdocs.access.application.port.output.SecretSource
import xyz.robinjoon.agentdocs.access.application.port.output.UserRepository
import xyz.robinjoon.agentdocs.access.application.service.EmergencyLoginService
import xyz.robinjoon.agentdocs.access.domain.TokenHash
import xyz.robinjoon.agentdocs.access.domain.emergency.EmergencyLoginGrant
import xyz.robinjoon.agentdocs.access.domain.user.GitHubAccount
import xyz.robinjoon.agentdocs.access.domain.user.User
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import xyz.robinjoon.agentdocs.access.domain.user.UserProfile
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class EmergencyLoginServiceTest {
    private val now = Instant.parse("2026-11-16T00:00:00Z")
    private val grants = mockk<EmergencyLoginRepository>()
    private val users = mockk<UserRepository>()
    private val secrets = mockk<SecretSource>()
    private val sessions = mockk<WebSessionUseCase>()
    private val owner = User.register(UserId(UUID.randomUUID()), GitHubAccount(42, "owner"), UserProfile(null, null), now)
    private val token = "ade_" + "a".repeat(43)
    private val grant = EmergencyLoginGrant.issue(TokenHash.of(token), owner.id, now)
    private val service =
        EmergencyLoginService(
            grants,
            users,
            secrets,
            sessions,
            AccessSettings("OWNER", emptySet(), "https://docs.example"),
            Clock.fixed(now, ZoneOffset.UTC),
        )

    @Test
    fun `only an existing current owner gets a link with its secret in the fragment`() {
        every { users.findByLogin("OWNER") } returns owner
        every { secrets.next() } returns "a".repeat(43)
        every { grants.add(any()) } just runs
        assertThat(service.issue()).isEqualTo("https://docs.example/emergency-login#$token")
        every { users.findByLogin("OWNER") } returns null
        assertThatIllegalStateException().isThrownBy { service.issue() }
    }

    @Test
    fun `a valid grant produces the existing web session token`() {
        every { grants.lock(TokenHash.of(token)) } returns grant
        every { users.findById(owner.id) } returns owner
        every { grants.update(any()) } just runs
        every { sessions.start(owner.id.value) } returns "adt_w_fake"
        assertThat(service.consume(token)).isEqualTo("adt_w_fake")
    }

    @Test
    fun `a changed owner and a missing user cannot recover a session`() {
        every { grants.lock(TokenHash.of(token)) } returns grant
        every { users.findById(owner.id) } returns owner.signedIn(GitHubAccount(42, "former-owner"), owner.profile, now)
        assertThatIllegalArgumentException().isThrownBy { service.consume(token) }
        every { users.findById(owner.id) } returns null
        assertThatIllegalArgumentException().isThrownBy { service.consume(token) }
    }

    @Test
    fun `unknown expired consumed and malformed tokens fail without a session result`() {
        every { grants.lock(TokenHash.of(token)) } returns null
        assertThatIllegalArgumentException().isThrownBy { service.consume(token) }
        every { grants.lock(TokenHash.of(token)) } returns grant.consume(now)
        assertThatIllegalArgumentException().isThrownBy { service.consume(token) }
        every { grants.lock(TokenHash.of(token)) } returns EmergencyLoginGrant.issue(TokenHash.of(token), owner.id, now.minusSeconds(600))
        assertThatIllegalArgumentException().isThrownBy { service.consume(token) }
        assertThatIllegalArgumentException().isThrownBy { service.consume("bad") }
    }
}
