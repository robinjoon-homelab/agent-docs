package xyz.robinjoon.agentdocs.access.application

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.clock
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.now
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.ownerId
import xyz.robinjoon.agentdocs.access.application.model.CallerScope
import xyz.robinjoon.agentdocs.access.application.port.output.SecretSource
import xyz.robinjoon.agentdocs.access.application.port.output.UserRepository
import xyz.robinjoon.agentdocs.access.application.port.output.WebSessionRepository
import xyz.robinjoon.agentdocs.access.application.service.WebSessionService
import xyz.robinjoon.agentdocs.access.domain.TokenHash
import xyz.robinjoon.agentdocs.access.domain.session.SessionTerm
import xyz.robinjoon.agentdocs.access.domain.session.WebSession
import xyz.robinjoon.agentdocs.access.domain.session.WebSessionId
import xyz.robinjoon.agentdocs.access.domain.user.Allowlist
import xyz.robinjoon.agentdocs.access.domain.user.GitHubAccount
import xyz.robinjoon.agentdocs.access.domain.user.User
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import xyz.robinjoon.agentdocs.access.domain.user.UserProfile
import java.time.Duration
import java.util.UUID

class WebSessionServiceTest {
    private val sessions = mockk<WebSessionRepository>()
    private val users = mockk<UserRepository>()
    private val secrets = mockk<SecretSource>()
    private val owner = User.restore(UserId(ownerId), GitHubAccount(42, "robinjoon"), UserProfile(null, null), now)

    private fun service(allowlist: Allowlist = Allowlist("robinjoon", emptySet())) =
        WebSessionService(sessions, users, allowlist, secrets, clock)

    private fun session(
        token: String,
        lastUsed: Duration,
    ): WebSession =
        WebSession.restore(
            WebSessionId(UUID.randomUUID()),
            UserId(ownerId),
            TokenHash.of(token),
            SessionTerm(now.minus(lastUsed), now.minus(lastUsed)),
        )

    @Test
    fun `a new sign-in hands the browser a token marked as a web sign-in`() {
        every { secrets.next() } returns "random"
        every { sessions.deleteUnusedBefore(any()) } just runs
        every { sessions.add(any()) } answers { firstArg() }

        assertThat(service().start(ownerId)).isEqualTo("adt_w_random")
    }

    @Test
    fun `a used sign-in resolves to the person with the role the allowlist gives now, renewed once a day`() {
        every { sessions.findByTokenHash(TokenHash.of("adt_w_today")) } returns session("adt_w_today", Duration.ofHours(2))
        every { sessions.findByTokenHash(TokenHash.of("adt_w_yesterday")) } returns session("adt_w_yesterday", Duration.ofDays(2))
        every { sessions.update(any()) } answers { firstArg() }
        every { users.findById(UserId(ownerId)) } returns owner

        val today = requireNotNull(service().callerFor("adt_w_today"))
        val yesterday = requireNotNull(service().callerFor("adt_w_yesterday"))

        assertThat(today.caller.owner).isTrue()
        assertThat(today.caller.scope).isEqualTo(CallerScope.OWNER)
        assertThat(today.renewed).isFalse()
        assertThat(yesterday.renewed).isTrue()
    }

    @Test
    fun `an unknown, foreign, long unused, or no longer allowed sign-in resolves to nobody`() {
        every { sessions.findByTokenHash(TokenHash.of("adt_w_unknown")) } returns null
        every { sessions.findByTokenHash(TokenHash.of("adt_w_stale")) } returns session("adt_w_stale", Duration.ofDays(30))
        every { sessions.findByTokenHash(TokenHash.of("adt_w_removed")) } returns session("adt_w_removed", Duration.ofHours(1))
        every { users.findById(UserId(ownerId)) } returns owner

        assertThat(service().callerFor("adt_w_unknown")).isNull()
        assertThat(service().callerFor("adt_a_agent_token")).isNull()
        assertThat(service().callerFor("adt_w_stale")).isNull()
        assertThat(service(Allowlist("someone-else", emptySet())).callerFor("adt_w_removed")).isNull()
    }
}
