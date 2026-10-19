package xyz.robinjoon.agentdocs.access.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionScope

internal object AuthorizationRequestsTable : Table("authorization_requests") {
    val id = javaUUID("id")
    val clientId = varchar("client_id", 500)
    val redirectUri = varchar("redirect_uri", 2000)
    val clientState = varchar("client_state", 500).nullable()
    val codeChallenge = varchar("code_challenge", 43)
    val resourceUri = varchar("resource_uri", 500)
    val requestedScope = enumerationByName("requested_scope", 10, ConnectionScope::class).nullable()
    val decision = varchar("decision", 10)
    val approverId = javaUUID("approver_id").nullable()
    val grantedScope = enumerationByName("granted_scope", 10, ConnectionScope::class).nullable()
    val grantedSpaces = text("granted_spaces").nullable()
    val lifetimeSeconds = long("lifetime_seconds").nullable()
    val codeHash = varchar("code_hash", 64).nullable()
    val expiresAt = timestampWithTimeZone("expires_at")

    override val primaryKey = PrimaryKey(id)
}
