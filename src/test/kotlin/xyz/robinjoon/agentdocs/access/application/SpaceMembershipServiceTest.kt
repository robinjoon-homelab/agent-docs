package xyz.robinjoon.agentdocs.access.application

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.agentCaller
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.clock
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.memberId
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.membership
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.now
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.owner
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.ownerId
import xyz.robinjoon.agentdocs.access.application.AccessFixtures.space
import xyz.robinjoon.agentdocs.access.application.model.Caller
import xyz.robinjoon.agentdocs.access.application.model.CallerScope
import xyz.robinjoon.agentdocs.access.application.model.MembershipInvite
import xyz.robinjoon.agentdocs.access.application.port.input.OwnerOnlyException
import xyz.robinjoon.agentdocs.access.application.port.input.SpaceMembershipNotFoundException
import xyz.robinjoon.agentdocs.access.application.port.output.SpaceMembershipRepository
import xyz.robinjoon.agentdocs.access.application.port.output.UserRepository
import xyz.robinjoon.agentdocs.access.application.service.AccessJournal
import xyz.robinjoon.agentdocs.access.application.service.SpaceMembershipService
import xyz.robinjoon.agentdocs.access.domain.InvalidAccessException
import xyz.robinjoon.agentdocs.access.domain.membership.MembershipTakenException
import xyz.robinjoon.agentdocs.access.domain.membership.SpaceMembershipId
import xyz.robinjoon.agentdocs.access.domain.membership.SpaceRole
import xyz.robinjoon.agentdocs.access.domain.user.Allowlist
import xyz.robinjoon.agentdocs.access.domain.user.GitHubAccount
import xyz.robinjoon.agentdocs.access.domain.user.User
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import xyz.robinjoon.agentdocs.access.domain.user.UserProfile
import xyz.robinjoon.agentdocs.application.port.output.ActivityReporter
import java.util.UUID

class SpaceMembershipServiceTest {
    private val memberships = mockk<SpaceMembershipRepository>()
    private val users = mockk<UserRepository>()
    private val activity = mockk<ActivityReporter>()
    private val service =
        SpaceMembershipService(memberships, users, Allowlist("robinjoon", setOf("octocat")), AccessJournal(activity, clock))
    private val bob = User.restore(UserId(memberId), GitHubAccount(77, "bob-renamed"), UserProfile(null, null), now)
    private val member = Caller(memberId, "bob", false, null, mapOf(space to CallerScope.EDIT), CallerScope.EDIT)

    init {
        every { activity.record(any()) } just runs
        every { users.findById(UserId(memberId)) } returns bob
    }

    @Test
    fun `the owner invites a login, which waits under the invited spelling until it signs in`() {
        every { users.findByLogin("Carol") } returns null
        every { memberships.findLiveBySpace(space) } returns emptyList()
        every { memberships.add(any()) } answers { firstArg() }

        val invited = service.invite(owner, MembershipInvite(space, " Carol ", SpaceRole.EDITOR))

        assertThat(invited.login).isEqualTo("Carol")
        assertThat(invited.membership.isPending).isTrue()
        assertThat(invited.membership.spaceId).isEqualTo(space)
        assertThat(invited.membership.role).isEqualTo(SpaceRole.EDITOR)
        assertThat(invited.membership.invitation.invitedBy).isEqualTo(UserId(ownerId))
        assertThat(invited.membership.invitation.invitedAt).isEqualTo(now)
    }

    @Test
    fun `a joined member is listed under the current login of the person`() {
        val joined = membership(space, "bob", SpaceRole.COMMENTER, memberId)
        val waiting = membership(space, "Carol", SpaceRole.EDITOR)
        every { memberships.findLiveBySpace(space) } returns listOf(joined, waiting)

        val listed = service.list(owner, space)

        assertThat(listed.map { it.login }).containsExactly("bob-renamed", "Carol")
        assertThat(listed.map { it.membership.isActive }).containsExactly(true, false)
    }

    @Test
    fun `a login already waiting in the space, compared without case, or a person already joined is taken`() {
        val waiting = membership(space, "Carol", SpaceRole.EDITOR)
        val joined = membership(space, "bob", SpaceRole.COMMENTER, memberId)
        every { memberships.findLiveBySpace(space) } returns listOf(waiting, joined)
        every { users.findByLogin("CAROL") } returns null
        every { users.findByLogin("bob-renamed") } returns bob

        assertThatThrownBy { service.invite(owner, MembershipInvite(space, "CAROL", SpaceRole.COMMENTER)) }
            .isInstanceOfSatisfying(MembershipTakenException::class.java) { assertThat(it.existing).isEqualTo(waiting.id) }
        assertThatThrownBy { service.invite(owner, MembershipInvite(space, "bob-renamed", SpaceRole.EDITOR)) }
            .isInstanceOfSatisfying(MembershipTakenException::class.java) { assertThat(it.existing).isEqualTo(joined.id) }
    }

    @Test
    fun `the old login of a renamed member can be invited for whoever holds it now`() {
        val joined = membership(space, "bob", SpaceRole.COMMENTER, memberId)
        every { memberships.findLiveBySpace(space) } returns listOf(joined)
        every { users.findByLogin("bob") } returns null
        every { memberships.add(any()) } answers { firstArg() }

        val invited = service.invite(owner, MembershipInvite(space, "bob", SpaceRole.EDITOR))

        assertThat(invited.membership.isPending).isTrue()
        assertThat(invited.membership.id).isNotEqualTo(joined.id)
    }

    @Test
    fun `the owner's own login and a login GitHub could not have are refused`() {
        assertThatThrownBy { service.invite(owner, MembershipInvite(space, "RobinJoon", SpaceRole.EDITOR)) }
            .isInstanceOf(InvalidAccessException::class.java)
            .hasMessage("the owner already sees every space")
        assertThatThrownBy { service.invite(owner, MembershipInvite(space, "not a login", SpaceRole.EDITOR)) }
            .isInstanceOf(InvalidAccessException::class.java)
    }

    @Test
    fun `an invitation needs single inner hyphens while a mixed case login is invited as typed`() {
        every { users.findByLogin(any()) } returns null
        every { memberships.findLiveBySpace(space) } returns emptyList()
        every { memberships.add(any()) } answers { firstArg() }

        listOf("-carol", "carol-", "ca--rol").forEach { login ->
            assertThatThrownBy { service.invite(owner, MembershipInvite(space, login, SpaceRole.EDITOR)) }
                .`as`(login)
                .isInstanceOf(InvalidAccessException::class.java)
        }
        assertThat(service.invite(owner, MembershipInvite(space, "Carol-Lee2", SpaceRole.EDITOR)).login).isEqualTo("Carol-Lee2")
    }

    @Test
    fun `a malformed login is invalid even when an old invitation of the space spells it the same`() {
        every { users.findByLogin(any()) } returns null
        every { memberships.findLiveBySpace(space) } returns listOf(membership(space, "bob-", SpaceRole.COMMENTER))

        assertThatThrownBy { service.invite(owner, MembershipInvite(space, "bob-", SpaceRole.EDITOR)) }
            .isInstanceOf(InvalidAccessException::class.java)
            .hasMessage("an invited GitHub login is 1 to 39 letters or digits with single hyphens only between them")
        assertThatThrownBy { service.invite(owner, MembershipInvite(space, "BOB-", SpaceRole.EDITOR)) }
            .isInstanceOf(InvalidAccessException::class.java)
    }

    @Test
    fun `members and every agent, the owner's included, cannot manage memberships`() {
        val id = SpaceMembershipId(UUID.randomUUID())
        listOf(member, agentCaller).forEach { caller ->
            assertThatThrownBy { service.list(caller, space) }.isInstanceOf(OwnerOnlyException::class.java)
            assertThatThrownBy { service.invite(caller, MembershipInvite(space, "carol", SpaceRole.EDITOR)) }
                .isInstanceOf(OwnerOnlyException::class.java)
            assertThatThrownBy { service.changeRole(caller, id, SpaceRole.EDITOR) }.isInstanceOf(OwnerOnlyException::class.java)
            assertThatThrownBy { service.revoke(caller, id) }.isInstanceOf(OwnerOnlyException::class.java)
        }
    }

    @Test
    fun `a role change answers with the changed membership and the same role keeps it as it is`() {
        val joined = membership(space, "bob", SpaceRole.COMMENTER, memberId)
        every { memberships.findByIdForUpdate(joined.id) } returns joined
        every { memberships.update(any()) } answers { firstArg() }

        val editor = service.changeRole(owner, joined.id, SpaceRole.EDITOR)
        val same = service.changeRole(owner, joined.id, SpaceRole.COMMENTER)

        assertThat(editor.membership.role).isEqualTo(SpaceRole.EDITOR)
        assertThat(editor.login).isEqualTo("bob-renamed")
        assertThat(same.membership).isEqualTo(joined)
    }

    @Test
    fun `an unknown or revoked membership cannot change, and revoking it again changes nothing`() {
        val unknown = SpaceMembershipId(UUID.randomUUID())
        val revoked = requireNotNull(membership(space, "carol", SpaceRole.EDITOR).revoke(now))
        every { memberships.findByIdForUpdate(unknown) } returns null
        every { memberships.findByIdForUpdate(revoked.id) } returns revoked

        assertThatThrownBy {
            service.changeRole(
                owner,
                unknown,
                SpaceRole.EDITOR,
            )
        }.isInstanceOf(SpaceMembershipNotFoundException::class.java)
        assertThatThrownBy { service.changeRole(owner, revoked.id, SpaceRole.COMMENTER) }
            .isInstanceOf(SpaceMembershipNotFoundException::class.java)
        assertThatThrownBy { service.revoke(owner, unknown) }.isInstanceOf(SpaceMembershipNotFoundException::class.java)
        service.revoke(owner, revoked.id)
    }
}
