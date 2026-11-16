package xyz.robinjoon.agentdocs.access.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import xyz.robinjoon.agentdocs.access.application.port.output.SpaceMembershipRepository
import xyz.robinjoon.agentdocs.access.domain.GitHubLogin
import xyz.robinjoon.agentdocs.access.domain.membership.MembershipState
import xyz.robinjoon.agentdocs.access.domain.membership.MembershipTakenException
import xyz.robinjoon.agentdocs.access.domain.membership.SpaceInvitation
import xyz.robinjoon.agentdocs.access.domain.membership.SpaceMembership
import xyz.robinjoon.agentdocs.access.domain.membership.SpaceMembershipId
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

// The live keys are derived from the state on every write: a waiting invitation holds its lowercase login, a joined
// membership its user, a revoked one neither. The unique keys on them are the last guard against two live memberships
// of one account in a space.
class ExposedSpaceMembershipRepository : SpaceMembershipRepository {
    override fun add(membership: SpaceMembership): SpaceMembership {
        guarded {
            SpaceMembershipsTable.insert {
                it[id] = membership.id.value
                it[spaceId] = membership.spaceId
                it[inviteeLogin] = membership.invitation.login.value
                it[invitedBy] = membership.invitation.invitedBy.value
                it[invitedAt] = utc(membership.invitation.invitedAt)
                write(it, membership)
            }
        }
        return requireNotNull(find(membership.id))
    }

    // The invitation never changes; the role, the joined user, and the revocation do.
    override fun update(membership: SpaceMembership): SpaceMembership {
        val updated =
            guarded { SpaceMembershipsTable.update({ SpaceMembershipsTable.id eq membership.id.value }) { write(it, membership) } }
        check(updated == 1) { "Membership to update does not exist: ${membership.id}" }
        return requireNotNull(find(membership.id))
    }

    override fun findByIdForUpdate(id: SpaceMembershipId): SpaceMembership? =
        select { SpaceMembershipsTable.id eq id.value }.forUpdate().singleOrNull()?.toMembership()

    override fun findLiveBySpace(space: UUID): List<SpaceMembership> =
        select { (SpaceMembershipsTable.spaceId eq space) and SpaceMembershipsTable.revokedAt.isNull() }
            .orderBy(SpaceMembershipsTable.invitedAt to SortOrder.ASC, SpaceMembershipsTable.id to SortOrder.ASC)
            .map { it.toMembership() }

    override fun findActiveByMember(member: UserId): List<SpaceMembership> =
        select { SpaceMembershipsTable.liveMember eq member.value }.map { it.toMembership() }

    override fun lockPendingByLogin(login: GitHubLogin): List<SpaceMembership> =
        select { SpaceMembershipsTable.liveLogin eq login.key }
            .orderBy(SpaceMembershipsTable.id to SortOrder.ASC)
            .forUpdate()
            .map { it.toMembership() }

    private fun find(id: SpaceMembershipId): SpaceMembership? =
        select { SpaceMembershipsTable.id eq id.value }.singleOrNull()?.toMembership()

    private fun select(condition: () -> Op<Boolean>): Query = SpaceMembershipsTable.selectAll().where(condition)

    private fun write(
        statement: UpdateBuilder<*>,
        membership: SpaceMembership,
    ) {
        statement[SpaceMembershipsTable.role] = membership.state.role
        statement[SpaceMembershipsTable.memberId] = membership.state.member?.value
        statement[SpaceMembershipsTable.joinedAt] = membership.state.joinedAt?.let(::utc)
        statement[SpaceMembershipsTable.revokedAt] = membership.state.revokedAt?.let(::utc)
        statement[SpaceMembershipsTable.liveLogin] =
            membership.invitation.login.key
                .takeIf { membership.isPending }
        statement[SpaceMembershipsTable.liveMember] = membership.member?.value?.takeIf { membership.isActive }
    }

    private fun <T> guarded(write: () -> T): T =
        try {
            write()
        } catch (exception: ExposedSQLException) {
            // PostgreSQL and H2 both report a unique constraint violation as SQLSTATE 23505.
            if (exception.sqlState == "23505") throw MembershipTakenException(null, exception)
            throw exception
        }

    private fun ResultRow.toMembership(): SpaceMembership {
        val invitation =
            SpaceInvitation(
                this[SpaceMembershipsTable.spaceId],
                GitHubLogin(this[SpaceMembershipsTable.inviteeLogin]),
                UserId(this[SpaceMembershipsTable.invitedBy]),
                this[SpaceMembershipsTable.invitedAt].toInstant(),
            )
        val state =
            MembershipState(
                this[SpaceMembershipsTable.role],
                this[SpaceMembershipsTable.memberId]?.let(::UserId),
                this[SpaceMembershipsTable.joinedAt]?.toInstant(),
                this[SpaceMembershipsTable.revokedAt]?.toInstant(),
            )
        return SpaceMembership.restore(SpaceMembershipId(this[SpaceMembershipsTable.id]), invitation, state)
    }

    private fun utc(instant: Instant) = instant.atOffset(ZoneOffset.UTC)
}
