package xyz.robinjoon.agentdocs.access.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.access.domain.GitHubLogin
import xyz.robinjoon.agentdocs.access.domain.membership.MembershipTakenException
import xyz.robinjoon.agentdocs.access.domain.membership.SpaceInvitation
import xyz.robinjoon.agentdocs.access.domain.membership.SpaceMembership
import xyz.robinjoon.agentdocs.access.domain.membership.SpaceMembershipId
import xyz.robinjoon.agentdocs.access.domain.membership.SpaceRole
import xyz.robinjoon.agentdocs.access.domain.user.GitHubAccount
import xyz.robinjoon.agentdocs.access.domain.user.User
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import xyz.robinjoon.agentdocs.access.domain.user.UserProfile
import xyz.robinjoon.agentdocs.support.persistence.MigratedH2Database
import java.time.Instant
import java.util.UUID

class ExposedSpaceMembershipRepositoryTest {
    private val database = MigratedH2Database()
    private val users = ExposedUserRepository()
    private val memberships = ExposedSpaceMembershipRepository()
    private val at = Instant.parse("2026-11-16T03:00:00.123456Z")
    private val space = UUID.fromString("33333333-3333-4333-8333-333333333333")
    private val otherSpace = UUID.fromString("44444444-4444-4444-8444-444444444444")
    private val owner = User.register(UserId(UUID.randomUUID()), GitHubAccount(42, "robinjoon"), UserProfile(null, null), at)
    private val bob = User.register(UserId(UUID.randomUUID()), GitHubAccount(77, "bob"), UserProfile(null, null), at)

    @BeforeEach
    fun addUsers() {
        database.inTransaction {
            users.add(owner)
            users.add(bob)
        }
    }

    @AfterEach
    fun closeDatabase() = database.close()

    private fun invite(
        login: String,
        space: UUID = this.space,
        role: SpaceRole = SpaceRole.COMMENTER,
        after: Long = 0,
    ): SpaceMembership =
        SpaceMembership.invite(
            SpaceMembershipId(UUID.randomUUID()),
            SpaceInvitation(space, GitHubLogin(login), owner.id, at.plusSeconds(after)),
            role,
        )

    @Test
    fun `an invitation round trips and waits under its login compared without case`() {
        val invited = invite("Bob")
        database.inTransaction { memberships.add(invited) }

        assertThat(database.inTransaction { memberships.findLiveBySpace(space) }).containsExactly(invited)
        assertThat(database.inTransaction { memberships.lockPendingByLogin(GitHubLogin("BOB")) }).containsExactly(invited)
        assertThat(database.inTransaction { memberships.findActiveByMember(bob.id) }).isEmpty()
        assertThat(database.inTransaction { memberships.findByIdForUpdate(invited.id) }).isEqualTo(invited)
        assertThat(database.inTransaction { memberships.findLiveBySpace(otherSpace) }).isEmpty()
    }

    @Test
    fun `a joined membership is found by its user and no longer waits for the login`() {
        val invited = invite("bob", role = SpaceRole.EDITOR)
        database.inTransaction { memberships.add(invited) }
        val joined = requireNotNull(invited.join(bob.id, at.plusSeconds(60)))

        database.inTransaction { memberships.update(joined) }

        assertThat(database.inTransaction { memberships.findActiveByMember(bob.id) }).containsExactly(joined)
        assertThat(database.inTransaction { memberships.lockPendingByLogin(GitHubLogin("bob")) }).isEmpty()
        assertThat(database.inTransaction { memberships.findLiveBySpace(space) }).containsExactly(joined)
    }

    @Test
    fun `a revoked membership stays stored but is no longer live, so the login can be invited again`() {
        val invited = invite("bob")
        database.inTransaction { memberships.add(invited) }
        val revoked = requireNotNull(requireNotNull(invited.join(bob.id, at)).revoke(at.plusSeconds(1)))
        database.inTransaction { memberships.update(revoked) }
        val again = invite("bob", after = 2)

        database.inTransaction { memberships.add(again) }

        assertThat(database.inTransaction { memberships.findByIdForUpdate(invited.id) }).isEqualTo(revoked)
        assertThat(database.inTransaction { memberships.findLiveBySpace(space) }).containsExactly(again)
        assertThat(database.inTransaction { memberships.findActiveByMember(bob.id) }).isEmpty()
    }

    @Test
    fun `a second live invitation of a login or a second joined membership of a person in one space is taken`() {
        val first = invite("bob")
        database.inTransaction { memberships.add(first) }

        assertThatThrownBy { database.inTransaction { memberships.add(invite("BOB", role = SpaceRole.EDITOR)) } }
            .isInstanceOf(MembershipTakenException::class.java)
        database.inTransaction { memberships.add(invite("bob", space = otherSpace)) }

        database.inTransaction { memberships.update(requireNotNull(first.join(bob.id, at))) }
        val renamed = invite("bob-renamed")
        database.inTransaction { memberships.add(renamed) }
        assertThatThrownBy { database.inTransaction { memberships.update(requireNotNull(renamed.join(bob.id, at))) } }
            .isInstanceOf(MembershipTakenException::class.java)
    }

    @Test
    fun `memberships of a space list oldest invitation first`() {
        val later = invite("carol", after = 10)
        val earlier = invite("dave", after = 5)
        database.inTransaction {
            memberships.add(later)
            memberships.add(earlier)
        }

        assertThat(database.inTransaction { memberships.findLiveBySpace(space) }.map { it.id }).containsExactly(earlier.id, later.id)
    }
}
