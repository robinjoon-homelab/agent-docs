package xyz.robinjoon.agentdocs.access.domain.membership

import xyz.robinjoon.agentdocs.access.domain.GitHubLogin
import xyz.robinjoon.agentdocs.access.domain.InvalidAccessException
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import xyz.robinjoon.agentdocs.domain.AggregateRoot
import java.time.Instant
import java.util.UUID

// One person's role in one space. The owner invites a GitHub login, which also lets that login sign in. The first
// GitHub sign-in with the login fixes the membership to the signed-in user's id, so a renamed login keeps it and
// whoever takes the old name later does not. A pending invitation grants no space until then. A revoked membership
// stays as a record and never comes back; inviting again makes a new one.
@AggregateRoot
@ConsistentCopyVisibility
data class SpaceMembership private constructor(
    val id: SpaceMembershipId,
    val invitation: SpaceInvitation,
    val state: MembershipState,
) {
    val spaceId: UUID get() = invitation.spaceId

    val role: SpaceRole get() = state.role

    val member: UserId? get() = state.member

    val isLive: Boolean get() = state.revokedAt == null

    val isPending: Boolean get() = isLive && state.member == null

    val isActive: Boolean get() = isLive && state.member != null

    // Only a live invitation not yet fixed to anyone can be joined; anything else stays as it is.
    fun join(
        user: UserId,
        at: Instant,
    ): SpaceMembership? = if (isPending) copy(state = state.copy(member = user, joinedAt = at)) else null

    fun changeRole(role: SpaceRole): SpaceMembership? {
        if (!isLive) throw InvalidAccessException("a revoked membership cannot change")
        return if (role == state.role) null else copy(state = state.copy(role = role))
    }

    fun revoke(at: Instant): SpaceMembership? = if (isLive) copy(state = state.copy(revokedAt = at)) else null

    companion object {
        // A new invitation takes only a login GitHub would let someone sign up with today. restore reads stored
        // memberships as they are, so none written before this rule is refused later. The use case checks this too,
        // before it looks for duplicates, so a malformed login is invalid even when an old invitation spells it.
        fun requireInvitable(login: GitHubLogin) {
            if (!GitHubLogin.isValidInvitation(login.value)) {
                throw InvalidAccessException("an invited GitHub login is 1 to 39 letters or digits with single hyphens only between them")
            }
        }

        fun invite(
            id: SpaceMembershipId,
            invitation: SpaceInvitation,
            role: SpaceRole,
        ): SpaceMembership {
            requireInvitable(invitation.login)
            return SpaceMembership(id, invitation, MembershipState(role, null, null, null))
        }

        fun restore(
            id: SpaceMembershipId,
            invitation: SpaceInvitation,
            state: MembershipState,
        ): SpaceMembership = SpaceMembership(id, invitation, state)
    }
}
