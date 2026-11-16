package xyz.robinjoon.agentdocs.access.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import xyz.robinjoon.agentdocs.access.domain.membership.SpaceRole

internal object SpaceMembershipsTable : Table("space_memberships") {
    val id = javaUUID("id")
    val spaceId = javaUUID("space_id")
    val inviteeLogin = varchar("invitee_login", 39)
    val memberId = javaUUID("member_id").nullable()
    val role = enumerationByName("member_role", 10, SpaceRole::class)
    val invitedBy = javaUUID("invited_by")
    val invitedAt = timestampWithTimeZone("invited_at")
    val joinedAt = timestampWithTimeZone("joined_at").nullable()
    val revokedAt = timestampWithTimeZone("revoked_at").nullable()
    val liveLogin = varchar("live_login", 39).nullable()
    val liveMember = javaUUID("live_member").nullable()

    override val primaryKey = PrimaryKey(id)
}
