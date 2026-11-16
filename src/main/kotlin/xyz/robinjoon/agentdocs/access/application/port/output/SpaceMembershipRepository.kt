package xyz.robinjoon.agentdocs.access.application.port.output

import xyz.robinjoon.agentdocs.access.domain.GitHubLogin
import xyz.robinjoon.agentdocs.access.domain.membership.SpaceMembership
import xyz.robinjoon.agentdocs.access.domain.membership.SpaceMembershipId
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import java.util.UUID

interface SpaceMembershipRepository {
    // Both fail with MembershipTakenException when the space already has a live membership for the same login or user.
    fun add(membership: SpaceMembership): SpaceMembership

    fun update(membership: SpaceMembership): SpaceMembership

    fun findByIdForUpdate(id: SpaceMembershipId): SpaceMembership?

    // Oldest invitation first.
    fun findLiveBySpace(space: UUID): List<SpaceMembership>

    // The live memberships fixed to this user, which decide the spaces a member sees.
    fun findActiveByMember(member: UserId): List<SpaceMembership>

    // Locks the live invitations still waiting for this login, compared without case, so a sign-in fixes each at most
    // once and in turn with a revoke.
    fun lockPendingByLogin(login: GitHubLogin): List<SpaceMembership>
}
