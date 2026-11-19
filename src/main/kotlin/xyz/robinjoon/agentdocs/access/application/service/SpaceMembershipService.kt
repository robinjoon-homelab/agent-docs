package xyz.robinjoon.agentdocs.access.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.access.application.model.Caller
import xyz.robinjoon.agentdocs.access.application.model.MembershipInvite
import xyz.robinjoon.agentdocs.access.application.model.MembershipView
import xyz.robinjoon.agentdocs.access.application.port.input.OwnerOnlyException
import xyz.robinjoon.agentdocs.access.application.port.input.SpaceMembershipNotFoundException
import xyz.robinjoon.agentdocs.access.application.port.input.SpaceMembershipUseCase
import xyz.robinjoon.agentdocs.access.application.port.output.SpaceMembershipRepository
import xyz.robinjoon.agentdocs.access.application.port.output.UserRepository
import xyz.robinjoon.agentdocs.access.domain.GitHubLogin
import xyz.robinjoon.agentdocs.access.domain.InvalidAccessException
import xyz.robinjoon.agentdocs.access.domain.membership.MembershipTakenException
import xyz.robinjoon.agentdocs.access.domain.membership.SpaceInvitation
import xyz.robinjoon.agentdocs.access.domain.membership.SpaceMembership
import xyz.robinjoon.agentdocs.access.domain.membership.SpaceMembershipId
import xyz.robinjoon.agentdocs.access.domain.membership.SpaceRole
import xyz.robinjoon.agentdocs.access.domain.user.Allowlist
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import xyz.robinjoon.agentdocs.access.domain.user.UserRole
import xyz.robinjoon.agentdocs.application.model.ActivitySubject
import java.util.UUID

// Membership changes carry their target space and stay visible only in the instance log.
@Transactional(readOnly = true)
class SpaceMembershipService(
    private val memberships: SpaceMembershipRepository,
    private val users: UserRepository,
    private val allowlist: Allowlist,
    private val journal: AccessJournal,
) : SpaceMembershipUseCase {
    override fun list(
        caller: Caller,
        space: UUID,
    ): List<MembershipView> {
        requireOwner(caller, "listing members")
        return memberships.findLiveBySpace(space).map(::viewOf)
    }

    @Transactional
    override fun invite(
        caller: Caller,
        invite: MembershipInvite,
    ): MembershipView {
        requireOwner(caller, "inviting members")
        val login = GitHubLogin(invite.login.trim())
        if (allowlist.roleOf(login.value) == UserRole.OWNER) throw InvalidAccessException("the owner already sees every space")
        // Before the duplicate lookup, so an old invitation with a since disallowed spelling cannot turn this into a conflict.
        SpaceMembership.requireInvitable(login)
        // A joined member is found by the person, since the invited login may since have been renamed or taken.
        val known = users.findByLogin(login.value)?.id
        memberships
            .findLiveBySpace(invite.space)
            .firstOrNull { (it.isPending && it.invitation.login.key == login.key) || (known != null && it.member == known) }
            ?.let { throw MembershipTakenException(it.id) }
        val invitation = SpaceInvitation(invite.space, login, UserId(caller.userId), journal.now())
        val saved = viewOf(memberships.add(SpaceMembership.invite(SpaceMembershipId(UUID.randomUUID()), invitation, invite.role)))
        journal.record(caller, "membership.invited", "스페이스 멤버 초대: @${saved.login} ${nameOf(invite.role)}", targetOf(saved.membership))
        return saved
    }

    @Transactional
    override fun changeRole(
        caller: Caller,
        id: SpaceMembershipId,
        role: SpaceRole,
    ): MembershipView {
        requireOwner(caller, "changing a member's role")
        val current = memberships.findByIdForUpdate(id)?.takeIf { it.isLive } ?: throw SpaceMembershipNotFoundException(id)
        val changed = current.changeRole(role) ?: return viewOf(current)
        val saved = viewOf(memberships.update(changed))
        journal.record(caller, "membership.role_changed", "멤버 역할 변경: @${saved.login} ${nameOf(role)}", targetOf(saved.membership))
        return saved
    }

    @Transactional
    override fun revoke(
        caller: Caller,
        id: SpaceMembershipId,
    ) {
        requireOwner(caller, "revoking a membership")
        val current = memberships.findByIdForUpdate(id) ?: throw SpaceMembershipNotFoundException(id)
        val revoked = current.revoke(journal.now()) ?: return
        val saved = viewOf(memberships.update(revoked))
        journal.record(caller, "membership.revoked", "멤버 회수: @${saved.login}", targetOf(saved.membership))
    }

    private fun targetOf(membership: SpaceMembership): ActivitySubject =
        ActivitySubject(membership.spaceId, null, null, null, membership.id.value)

    private fun viewOf(membership: SpaceMembership): MembershipView {
        val current =
            membership.member
                ?.let(users::findById)
                ?.account
                ?.login
        return MembershipView(membership, current ?: membership.invitation.login.value)
    }

    private fun requireOwner(
        caller: Caller,
        action: String,
    ) {
        if (!caller.owner || caller.agent != null) throw OwnerOnlyException(action)
    }

    internal companion object {
        fun nameOf(role: SpaceRole): String =
            when (role) {
                SpaceRole.EDITOR -> "편집자"
                SpaceRole.COMMENTER -> "댓글 작성자"
            }
    }
}
