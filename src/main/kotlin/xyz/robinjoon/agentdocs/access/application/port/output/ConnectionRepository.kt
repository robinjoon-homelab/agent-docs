package xyz.robinjoon.agentdocs.access.application.port.output

import xyz.robinjoon.agentdocs.access.domain.TokenHash
import xyz.robinjoon.agentdocs.access.domain.connection.Connection
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionId
import xyz.robinjoon.agentdocs.access.domain.user.UserId

interface ConnectionRepository {
    fun add(connection: Connection): Connection

    fun update(connection: Connection): Connection

    fun findById(id: ConnectionId): Connection?

    fun findByAccessHash(hash: TokenHash): Connection?

    // Locks the connection, so two refreshes with one token cannot both succeed.
    fun findByRefreshHashForUpdate(hash: TokenHash): Connection?

    // Newest first.
    fun findByUser(user: UserId): List<Connection>
}
