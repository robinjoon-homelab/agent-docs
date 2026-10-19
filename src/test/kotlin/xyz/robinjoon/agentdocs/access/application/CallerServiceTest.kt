package xyz.robinjoon.agentdocs.access.application

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.clock
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.connection
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.now
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.ownerId
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.space
import xyz.robinjoon.agentdocs.access.application.model.CallerScope
import xyz.robinjoon.agentdocs.access.application.port.output.ConnectionRepository
import xyz.robinjoon.agentdocs.access.application.port.output.UserRepository
import xyz.robinjoon.agentdocs.access.application.service.CallerService
import xyz.robinjoon.agentdocs.access.domain.TokenHash
import xyz.robinjoon.agentdocs.access.domain.user.GitHubAccount
import xyz.robinjoon.agentdocs.access.domain.user.User
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import xyz.robinjoon.agentdocs.access.domain.user.UserProfile

class CallerServiceTest {
    private val connections = mockk<ConnectionRepository>()
    private val users = mockk<UserRepository>()
    private val service = CallerService(connections, users, clock)
    private val user = User.restore(UserId(ownerId), GitHubAccount(42, "robinjoon"), UserProfile(null, null), now)

    @Test
    fun `a valid token resolves to the agent with exactly its granted spaces and scope`() {
        val active = connection("adt_a_token", null)
        every { connections.findByAccessHash(TokenHash.of("adt_a_token")) } returns active
        every { users.findById(UserId(ownerId)) } returns user
        every { connections.update(any()) } answers { firstArg() }

        val caller = requireNotNull(service.callerFor("adt_a_token"))

        assertThat(caller.agent?.connectionId).isEqualTo(active.id.value)
        assertThat(caller.spaces).containsExactly(space)
        assertThat(caller.scope).isEqualTo(CallerScope.EDIT)
        assertThat(caller.owner).isFalse()
    }

    @Test
    fun `an unknown, foreign, or revoked token resolves to nobody`() {
        val revoked = requireNotNull(connection("adt_a_revoked", null).revoke(now.minusSeconds(1)))
        every { connections.findByAccessHash(TokenHash.of("adt_a_unknown")) } returns null
        every { connections.findByAccessHash(TokenHash.of("adt_a_revoked")) } returns revoked

        assertThat(service.callerFor("adt_a_unknown")).isNull()
        assertThat(service.callerFor("adt_a_revoked")).isNull()
        assertThat(service.callerFor("ghp_github_token")).isNull()
    }
}
