package xyz.robinjoon.agentdocs.access.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.access.application.model.Caller
import xyz.robinjoon.agentdocs.access.application.model.GrantChange
import xyz.robinjoon.agentdocs.access.application.model.IssuedToken
import xyz.robinjoon.agentdocs.access.application.model.ScriptTokenRequest
import xyz.robinjoon.agentdocs.access.application.port.input.ConnectionNotFoundException
import xyz.robinjoon.agentdocs.access.application.port.input.ConnectionUseCase
import xyz.robinjoon.agentdocs.access.application.port.input.OwnerOnlyException
import xyz.robinjoon.agentdocs.access.application.port.output.ConnectionRepository
import xyz.robinjoon.agentdocs.access.application.port.output.SecretSource
import xyz.robinjoon.agentdocs.access.domain.InvalidAccessException
import xyz.robinjoon.agentdocs.access.domain.connection.ClientRef
import xyz.robinjoon.agentdocs.access.domain.connection.Connection
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionId
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionKind
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionOwner
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionState
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionTerm
import xyz.robinjoon.agentdocs.access.domain.connection.Credentials
import xyz.robinjoon.agentdocs.access.domain.connection.Grant
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import java.time.Duration
import java.util.UUID

@Transactional(readOnly = true)
class ConnectionService(
    private val connections: ConnectionRepository,
    private val secrets: SecretSource,
    private val journal: AccessJournal,
) : ConnectionUseCase {
    override fun list(caller: Caller): List<Connection> {
        requireOwner(caller, "listing connections")
        return connections.findByUser(UserId(caller.userId))
    }

    // A script token is its own access token for the whole lifetime of the connection; it never refreshes.
    @Transactional
    override fun issue(
        caller: Caller,
        request: ScriptTokenRequest,
    ): IssuedToken {
        requireOwner(caller, "issuing a script token")
        val lifetime = ConnectionTerm.lifetimeOf(request.lifetimeDays)
        val now = journal.now()
        val (raw, hash) = Tokens.next(secrets, Tokens.SCRIPT)
        val owner = ConnectionOwner(UserId(caller.userId), ConnectionKind.SCRIPT, ClientRef(null, request.name.trim(), null))
        val term = ConnectionTerm(now, now.plus(lifetime), null, null)
        val state = ConnectionState(term, Credentials(hash, term.expiresAt, null))
        val connection =
            connections.add(
                Connection.issue(ConnectionId(UUID.randomUUID()), owner, Grant.of(request.spaces, request.scope), state),
            )
        journal.record(caller, "connection.issued", "스크립트 토큰 발급: ${connection.owner.client.name}")
        return IssuedToken(connection, raw)
    }

    @Transactional
    override fun regrant(
        caller: Caller,
        id: ConnectionId,
        change: GrantChange,
    ): Connection {
        val connection = owned(caller, id, "changing a connection")
        val grant = Grant.of(change.spaces ?: connection.grant.spaces, change.scope ?: connection.grant.scope)
        if (grant == connection.grant) return connection
        val saved = connections.update(connection.regrant(grant))
        journal.record(caller, "connection.regranted", "연결 권한 변경: ${saved.owner.client.name}")
        return saved
    }

    @Transactional
    override fun revoke(
        caller: Caller,
        id: ConnectionId,
    ): Connection {
        val connection = owned(caller, id, "revoking a connection")
        val revoked = connection.revoke(journal.now()) ?: return connection
        val saved = connections.update(revoked)
        journal.record(caller, "connection.revoked", "연결 회수: ${saved.owner.client.name}")
        return saved
    }

    private fun owned(
        caller: Caller,
        id: ConnectionId,
        action: String,
    ): Connection {
        requireOwner(caller, action)
        return connections.findById(id)?.takeIf { it.owner.userId.value == caller.userId } ?: throw ConnectionNotFoundException(id)
    }

    private fun requireOwner(
        caller: Caller,
        action: String,
    ) {
        if (!caller.owner || caller.agent != null) throw OwnerOnlyException(action)
    }
}
