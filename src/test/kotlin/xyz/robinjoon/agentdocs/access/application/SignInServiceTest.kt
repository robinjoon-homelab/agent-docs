package xyz.robinjoon.agentdocs.access.application

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.clock
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.memberId
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.membership
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.now
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.otherSpace
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.space
import xyz.robinjoon.agentdocs.access.application.model.CallerScope
import xyz.robinjoon.agentdocs.access.application.model.GitHubIdentity
import xyz.robinjoon.agentdocs.access.application.port.input.SignInRefusedException
import xyz.robinjoon.agentdocs.access.application.port.output.SpaceMembershipRepository
import xyz.robinjoon.agentdocs.access.application.port.output.UserRepository
import xyz.robinjoon.agentdocs.access.application.service.AccessJournal
import xyz.robinjoon.agentdocs.access.application.service.SignInService
import xyz.robinjoon.agentdocs.access.domain.GitHubLogin
import xyz.robinjoon.agentdocs.access.domain.membership.SpaceRole
import xyz.robinjoon.agentdocs.access.domain.user.Allowlist
import xyz.robinjoon.agentdocs.access.domain.user.GitHubAccount
import xyz.robinjoon.agentdocs.access.domain.user.User
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import xyz.robinjoon.agentdocs.access.domain.user.UserProfile
import xyz.robinjoon.agentdocs.application.port.output.ActivityReporter
import java.util.UUID

class SignInServiceTest {
    private val users = mockk<UserRepository>()
    private val memberships = mockk<SpaceMembershipRepository>()
    private val activity = mockk<ActivityReporter>()
    private val service = SignInService(users, memberships, Allowlist("robinjoon", setOf("octocat")), AccessJournal(activity, clock))
    private val bob = User.restore(UserId(memberId), GitHubAccount(77, "bob"), UserProfile(null, null), now.minusSeconds(86_400))

    init {
        every { memberships.lockPendingByLogin(any()) } returns emptyList()
        every { memberships.findActiveByMember(any()) } returns emptyList()
        every { activity.record(any()) } just runs
        every { users.update(any()) } answers { firstArg() }
        every { users.add(any()) } answers { firstArg() }
    }

    @Test
    fun `the owner signs in with every space and a renamed login is kept for the same GitHub id`() {
        val known = User.restore(UserId(UUID.randomUUID()), GitHubAccount(42, "old-name"), UserProfile(null, null), now.minusSeconds(3_600))
        every { users.findByGithubId(42) } returns known

        val caller = service.signIn(GitHubIdentity(42, "RobinJoon", "Robin", null))

        assertThat(caller.userId).isEqualTo(known.id.value)
        assertThat(caller.login).isEqualTo("RobinJoon")
        assertThat(caller.owner).isTrue()
        assertThat(caller.spaces).isNull()
    }

    @Test
    fun `a member signs in but sees no space yet`() {
        every { users.findByGithubId(7) } returns null

        val caller = service.signIn(GitHubIdentity(7, "octocat", null, null))

        assertThat(caller.owner).isFalse()
        assertThat(caller.spaces).isEmpty()
        assertThat(caller.scope).isEqualTo(CallerScope.READ)
    }

    @Test
    fun `an account outside the allowlist is refused before anything is stored`() {
        every { users.findByGithubId(9) } returns null

        assertThatThrownBy { service.signIn(GitHubIdentity(9, "stranger", null, null)) }
            .isInstanceOf(SignInRefusedException::class.java)
            .hasMessage("GitHub account stranger is not allowed to sign in")
    }

    @Test
    fun `an invited login signs in for the first time and its invitations are fixed to the new user`() {
        val waiting = listOf(membership(space, "Bob", SpaceRole.COMMENTER), membership(otherSpace, "bob", SpaceRole.EDITOR))
        every { users.findByGithubId(77) } returns null
        every { memberships.lockPendingByLogin(GitHubLogin("BOB")) } returns waiting
        every { memberships.update(any()) } answers { firstArg() }

        val caller = service.signIn(GitHubIdentity(77, "BOB", null, null))

        assertThat(caller.owner).isFalse()
        assertThat(caller.agent).isNull()
        assertThat(caller.spaces).containsExactlyInAnyOrderEntriesOf(mapOf(space to CallerScope.COMMENT, otherSpace to CallerScope.EDIT))
        assertThat(caller.scope).isEqualTo(CallerScope.EDIT)
    }

    @Test
    fun `a member keeps the joined spaces under a new login that nobody invited`() {
        every { users.findByGithubId(77) } returns bob
        every { memberships.findActiveByMember(UserId(memberId)) } returns listOf(membership(space, "bob", SpaceRole.EDITOR, memberId))

        val caller = service.signIn(GitHubIdentity(77, "bob-renamed", null, null))

        assertThat(caller.userId).isEqualTo(memberId)
        assertThat(caller.login).isEqualTo("bob-renamed")
        assertThat(caller.spaces).containsExactlyEntriesOf(mapOf(space to CallerScope.EDIT))
    }

    @Test
    fun `a second invitation to a space the person already joined leaves the joined role in place`() {
        every { users.findByGithubId(77) } returns bob
        every { memberships.findActiveByMember(UserId(memberId)) } returns listOf(membership(space, "bob", SpaceRole.EDITOR, memberId))
        every { memberships.lockPendingByLogin(GitHubLogin("bob")) } returns listOf(membership(space, "bob", SpaceRole.COMMENTER))

        val caller = service.signIn(GitHubIdentity(77, "bob", null, null))

        assertThat(caller.spaces).containsExactlyEntriesOf(mapOf(space to CallerScope.EDIT))
    }

    @Test
    fun `a person whose memberships were all revoked and who is not on the allowlist is refused`() {
        every { users.findByGithubId(77) } returns bob

        assertThatThrownBy { service.signIn(GitHubIdentity(77, "bob", null, null)) }.isInstanceOf(SignInRefusedException::class.java)
    }
}
