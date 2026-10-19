package xyz.robinjoon.agentdocs.access.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import xyz.robinjoon.agentdocs.access.application.port.output.ConnectionRepository
import xyz.robinjoon.agentdocs.access.domain.TokenHash
import xyz.robinjoon.agentdocs.access.domain.connection.ClientRef
import xyz.robinjoon.agentdocs.access.domain.connection.Connection
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionId
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionOwner
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionState
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionTerm
import xyz.robinjoon.agentdocs.access.domain.connection.Credentials
import xyz.robinjoon.agentdocs.access.domain.connection.Grant
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import java.time.Instant
import java.time.ZoneOffset

class ExposedConnectionRepository : ConnectionRepository {
    override fun add(connection: Connection): Connection {
        ConnectionsTable.insert {
            it[id] = connection.id.value
            it[userId] = connection.owner.userId.value
            it[kind] = connection.owner.kind
            it[clientId] = connection.owner.client.clientId
            it[clientName] = connection.owner.client.name
            it[clientHost] = connection.owner.client.host
            it[createdAt] = utc(connection.term.createdAt)
            write(it, connection)
        }
        writeSpaces(connection)
        return requireNotNull(findById(connection.id))
    }

    // The client and creation time never change; grant, term, and tokens do.
    override fun update(connection: Connection): Connection {
        val updated = ConnectionsTable.update({ ConnectionsTable.id eq connection.id.value }) { write(it, connection) }
        check(updated == 1) { "Connection to update does not exist: ${connection.id}" }
        ConnectionSpacesTable.deleteWhere { connectionId eq connection.id.value }
        writeSpaces(connection)
        return requireNotNull(findById(connection.id))
    }

    override fun findById(id: ConnectionId): Connection? = select { ConnectionsTable.id eq id.value }.singleOrNull()?.toConnection()

    override fun findByAccessHash(hash: TokenHash): Connection? =
        select {
            ConnectionsTable.accessHash eq hash.value
        }.singleOrNull()?.toConnection()

    override fun findByRefreshHashForUpdate(hash: TokenHash): Connection? =
        select { ConnectionsTable.refreshHash eq hash.value }.forUpdate().singleOrNull()?.toConnection()

    override fun findByUser(user: UserId): List<Connection> =
        select { ConnectionsTable.userId eq user.value }.orderBy(ConnectionsTable.createdAt to SortOrder.DESC).map { it.toConnection() }

    private fun select(condition: () -> Op<Boolean>): Query = ConnectionsTable.selectAll().where(condition)

    private fun write(
        statement: UpdateBuilder<*>,
        connection: Connection,
    ) {
        statement[ConnectionsTable.scope] = connection.grant.scope
        statement[ConnectionsTable.expiresAt] = utc(connection.term.expiresAt)
        statement[ConnectionsTable.revokedAt] = connection.term.revokedAt?.let(::utc)
        statement[ConnectionsTable.lastUsedAt] = connection.term.lastUsedAt?.let(::utc)
        statement[ConnectionsTable.accessHash] = connection.credentials.accessHash.value
        statement[ConnectionsTable.accessExpiresAt] = utc(connection.credentials.accessExpiresAt)
        statement[ConnectionsTable.refreshHash] = connection.credentials.refreshHash?.value
    }

    private fun writeSpaces(connection: Connection) {
        ConnectionSpacesTable.batchInsert(connection.grant.spaces, shouldReturnGeneratedValues = false) { space ->
            this[ConnectionSpacesTable.connectionId] = connection.id.value
            this[ConnectionSpacesTable.spaceId] = space
        }
    }

    private fun ResultRow.toConnection(): Connection {
        val id = this[ConnectionsTable.id]
        val spaces =
            ConnectionSpacesTable
                .selectAll()
                .where {
                    ConnectionSpacesTable.connectionId eq id
                }.map { it[ConnectionSpacesTable.spaceId] }
        val owner =
            ConnectionOwner(
                UserId(this[ConnectionsTable.userId]),
                this[ConnectionsTable.kind],
                ClientRef(this[ConnectionsTable.clientId], this[ConnectionsTable.clientName], this[ConnectionsTable.clientHost]),
            )
        return Connection.restore(ConnectionId(id), owner, Grant.of(spaces.toSet(), this[ConnectionsTable.scope]), stateOf(this))
    }

    private fun stateOf(row: ResultRow): ConnectionState {
        val term =
            ConnectionTerm(
                row[ConnectionsTable.createdAt].toInstant(),
                row[ConnectionsTable.expiresAt].toInstant(),
                row[ConnectionsTable.revokedAt]?.toInstant(),
                row[ConnectionsTable.lastUsedAt]?.toInstant(),
            )
        val credentials =
            Credentials(
                TokenHash(row[ConnectionsTable.accessHash]),
                row[ConnectionsTable.accessExpiresAt].toInstant(),
                row[ConnectionsTable.refreshHash]?.let(::TokenHash),
            )
        return ConnectionState(term, credentials)
    }

    private fun utc(instant: Instant) = instant.atOffset(ZoneOffset.UTC)
}
