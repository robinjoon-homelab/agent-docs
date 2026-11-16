package xyz.robinjoon.agentdocs.access.domain.membership

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.access.domain.GitHubLogin
import xyz.robinjoon.agentdocs.access.domain.InvalidAccessException
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import java.time.Instant
import java.util.UUID

class SpaceMembershipTest {
    private val at = Instant.parse("2026-11-16T03:00:00Z")
    private val owner = UserId(UUID.fromString("11111111-1111-4111-8111-111111111111"))
    private val bob = UserId(UUID.fromString("77777777-7777-4777-8777-777777777777"))
    private val invitation = SpaceInvitation(UUID.fromString("33333333-3333-4333-8333-333333333333"), GitHubLogin("Bob"), owner, at)
    private val invited = SpaceMembership.invite(SpaceMembershipId(UUID.randomUUID()), invitation, SpaceRole.COMMENTER)

    @Test
    fun `an invitation waits for its login and grants nothing until a sign-in fixes it to the user`() {
        assertThat(invited.isPending).isTrue()
        assertThat(invited.isActive).isFalse()
        assertThat(invited.member).isNull()

        val joined = requireNotNull(invited.join(bob, at.plusSeconds(60)))

        assertThat(joined.isActive).isTrue()
        assertThat(joined.isPending).isFalse()
        assertThat(joined.member).isEqualTo(bob)
        assertThat(joined.state.joinedAt).isEqualTo(at.plusSeconds(60))
        assertThat(joined.invitation).isEqualTo(invitation)
        assertThat(joined.join(UserId(UUID.randomUUID()), at.plusSeconds(120))).isNull()
    }

    @Test
    fun `a role changes while the membership lives and the same role changes nothing`() {
        val editor = requireNotNull(invited.changeRole(SpaceRole.EDITOR))

        assertThat(editor.role).isEqualTo(SpaceRole.EDITOR)
        assertThat(editor.changeRole(SpaceRole.EDITOR)).isNull()
    }

    @Test
    fun `a revoked membership stays revoked, grants nothing, and cannot change or be joined`() {
        val revoked = requireNotNull(requireNotNull(invited.join(bob, at)).revoke(at.plusSeconds(1)))

        assertThat(revoked.isLive).isFalse()
        assertThat(revoked.isActive).isFalse()
        assertThat(revoked.isPending).isFalse()
        assertThat(revoked.revoke(at.plusSeconds(2))).isNull()
        assertThat(requireNotNull(invited.revoke(at)).join(bob, at)).isNull()
        assertThatThrownBy { revoked.changeRole(SpaceRole.EDITOR) }.isInstanceOf(InvalidAccessException::class.java)
    }

    @Test
    fun `a stored state fixed to a user without a join time, or the reverse, is refused`() {
        assertThatThrownBy { MembershipState(SpaceRole.EDITOR, bob, null, null) }.isInstanceOf(InvalidAccessException::class.java)
        assertThatThrownBy { MembershipState(SpaceRole.EDITOR, null, at, null) }.isInstanceOf(InvalidAccessException::class.java)
    }

    @Test
    fun `roles read from the wire without case and anything else is unknown`() {
        assertThat(SpaceRole.of(" Editor ")).isEqualTo(SpaceRole.EDITOR)
        assertThat(SpaceRole.of("commenter")).isEqualTo(SpaceRole.COMMENTER)
        assertThat(SpaceRole.of("owner")).isNull()
        assertThat(SpaceRole.COMMENTER.wire).isEqualTo("commenter")
    }

    @Test
    fun `a new invitation refuses hyphens GitHub no longer allows but a stored one is read as it was`() {
        listOf("bob-", "bob--smith").forEach { login ->
            val legacy = invitation.copy(login = GitHubLogin(login))
            assertThatThrownBy { SpaceMembership.invite(SpaceMembershipId(UUID.randomUUID()), legacy, SpaceRole.EDITOR) }
                .`as`(login)
                .isInstanceOf(InvalidAccessException::class.java)
                .hasMessage("an invited GitHub login is 1 to 39 letters or digits with single hyphens only between them")
            val stored =
                SpaceMembership.restore(
                    SpaceMembershipId(UUID.randomUUID()),
                    legacy,
                    MembershipState(SpaceRole.EDITOR, bob, at, null),
                )
            assertThat(stored.invitation.login.value).isEqualTo(login)
            assertThat(stored.isActive).isTrue()
        }
        assertThatThrownBy { GitHubLogin("-bob") }.isInstanceOf(InvalidAccessException::class.java)
        val mixed =
            SpaceMembership.invite(
                SpaceMembershipId(UUID.randomUUID()),
                invitation.copy(login = GitHubLogin("Bob-Smith2")),
                SpaceRole.EDITOR,
            )
        assertThat(mixed.invitation.login.key).isEqualTo("bob-smith2")
    }
}
