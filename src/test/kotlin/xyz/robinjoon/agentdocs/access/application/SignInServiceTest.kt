package xyz.robinjoon.agentdocs.access.application

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.clock
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.now
import xyz.robinjoon.agentdocs.access.application.model.CallerScope
import xyz.robinjoon.agentdocs.access.application.model.GitHubIdentity
import xyz.robinjoon.agentdocs.access.application.port.input.SignInRefusedException
import xyz.robinjoon.agentdocs.access.application.port.output.UserRepository
import xyz.robinjoon.agentdocs.access.application.service.SignInService
import xyz.robinjoon.agentdocs.access.domain.user.Allowlist
import xyz.robinjoon.agentdocs.access.domain.user.GitHubAccount
import xyz.robinjoon.agentdocs.access.domain.user.User
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import xyz.robinjoon.agentdocs.access.domain.user.UserProfile
import java.util.UUID

class SignInServiceTest {
    private val users = mockk<UserRepository>()
    private val service = SignInService(users, Allowlist("robinjoon", setOf("octocat")), clock)

    @Test
    fun `the owner signs in with every space and a renamed login is kept for the same GitHub id`() {
        val known = User.restore(UserId(UUID.randomUUID()), GitHubAccount(42, "old-name"), UserProfile(null, null), now.minusSeconds(3_600))
        every { users.findByGithubId(42) } returns known
        every { users.update(any()) } answers { firstArg() }

        val caller = service.signIn(GitHubIdentity(42, "RobinJoon", "Robin", null))

        assertThat(caller.userId).isEqualTo(known.id.value)
        assertThat(caller.login).isEqualTo("RobinJoon")
        assertThat(caller.owner).isTrue()
        assertThat(caller.spaces).isNull()
    }

    @Test
    fun `a member signs in but sees no space yet`() {
        every { users.findByGithubId(7) } returns null
        every { users.add(any()) } answers { firstArg() }

        val caller = service.signIn(GitHubIdentity(7, "octocat", null, null))

        assertThat(caller.owner).isFalse()
        assertThat(caller.spaces).isEmpty()
        assertThat(caller.scope).isEqualTo(CallerScope.READ)
    }

    @Test
    fun `an account outside the allowlist is refused before anything is stored`() {
        assertThatThrownBy { service.signIn(GitHubIdentity(9, "stranger", null, null)) }
            .isInstanceOf(SignInRefusedException::class.java)
            .hasMessage("GitHub account stranger is not allowed to sign in")
    }
}
