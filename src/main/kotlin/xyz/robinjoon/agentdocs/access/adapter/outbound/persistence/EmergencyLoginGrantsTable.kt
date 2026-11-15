package xyz.robinjoon.agentdocs.access.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object EmergencyLoginGrantsTable : Table("emergency_login_grants") {
    val tokenHash = varchar("token_hash", 64)
    val userId = javaUUID("user_id").references(UsersTable.id)
    val expiresAt = timestampWithTimeZone("expires_at")
    val consumed = bool("consumed")
    override val primaryKey = PrimaryKey(tokenHash)
}
