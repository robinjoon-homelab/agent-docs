package xyz.robinjoon.agentdocs.access.application.port.input

import xyz.robinjoon.agentdocs.access.application.model.Caller
import xyz.robinjoon.agentdocs.access.application.model.MembershipInvite
import xyz.robinjoon.agentdocs.access.application.model.MembershipView
import xyz.robinjoon.agentdocs.access.domain.membership.SpaceMembershipId
import xyz.robinjoon.agentdocs.access.domain.membership.SpaceRole
import java.util.UUID

// Who may enter which space. The human owner only; anyone else, an agent of the owner included, fails with
// OwnerOnlyException. Every change applies from the member's next request.
interface SpaceMembershipUseCase {
    // The space's live memberships, oldest invitation first. The space id is taken by value and never checked.
    fun list(
        caller: Caller,
        space: UUID,
    ): List<MembershipView>

    // Fails with MembershipTakenException when the space already has a live membership for that login or person, and
    // with InvalidAccessException for a malformed login or the owner's own.
    fun invite(
        caller: Caller,
        invite: MembershipInvite,
    ): MembershipView

    // The same role changes nothing. Fails with SpaceMembershipNotFoundException for an unknown or revoked membership.
    fun changeRole(
        caller: Caller,
        id: SpaceMembershipId,
        role: SpaceRole,
    ): MembershipView

    // Keeps the record; revoking a revoked membership changes nothing. Fails with SpaceMembershipNotFoundException for
    // an unknown one.
    fun revoke(
        caller: Caller,
        id: SpaceMembershipId,
    )
}
