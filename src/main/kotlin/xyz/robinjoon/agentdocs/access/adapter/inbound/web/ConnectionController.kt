package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import xyz.robinjoon.agentdocs.access.application.model.GrantChange
import xyz.robinjoon.agentdocs.access.application.model.ScriptTokenRequest
import xyz.robinjoon.agentdocs.access.application.port.input.ConnectionUseCase
import xyz.robinjoon.agentdocs.access.domain.InvalidAccessException
import xyz.robinjoon.agentdocs.access.domain.connection.Connection
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionId
import xyz.robinjoon.agentdocs.access.domain.connection.ConnectionScope
import java.net.URI
import java.security.Principal
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/connections")
class ConnectionController(
    private val connections: ConnectionUseCase,
) {
    @GetMapping
    fun list(principal: Principal?): List<ConnectionResponse> = connections.list(Callers.of(principal)).map(ConnectionResponse::from)

    // The raw token appears in this response only.
    @PostMapping
    fun issue(
        @RequestBody request: IssueTokenRequest,
        principal: Principal?,
    ): ResponseEntity<IssuedTokenResponse> {
        val issued =
            connections.issue(
                Callers.of(principal),
                ScriptTokenRequest(request.name, request.spaceIds.toSet(), scopeOf(request.scope), request.expiresInDays),
            )
        val body = IssuedTokenResponse(ConnectionResponse.from(issued.connection), issued.token)
        return ResponseEntity.created(URI.create("/api/connections/${issued.connection.id}")).body(body)
    }

    @PatchMapping("/{id}")
    fun regrant(
        @PathVariable id: UUID,
        @RequestBody request: RegrantRequest,
        principal: Principal?,
    ): ConnectionResponse {
        val change = GrantChange(request.spaceIds?.toSet(), request.scope?.let(::scopeOf))
        return ConnectionResponse.from(connections.regrant(Callers.of(principal), ConnectionId(id), change))
    }

    @DeleteMapping("/{id}")
    fun revoke(
        @PathVariable id: UUID,
        principal: Principal?,
    ): ResponseEntity<Unit> {
        connections.revoke(Callers.of(principal), ConnectionId(id))
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build()
    }

    private fun scopeOf(value: String): ConnectionScope =
        ConnectionScope.of(value) ?: throw InvalidAccessException("scope must be read, comment, or edit")
}

data class IssueTokenRequest(
    val name: String,
    val spaceIds: List<UUID>,
    val scope: String,
    val expiresInDays: Int,
)

data class RegrantRequest(
    val spaceIds: List<UUID>?,
    val scope: String?,
)

data class IssuedTokenResponse(
    val connection: ConnectionResponse,
    val token: String,
)

// Built from the connection rather than field by field, so the response stays one flat object.
class ConnectionResponse private constructor(
    connection: Connection,
) {
    val id: UUID = connection.id.value
    val kind: String =
        connection.owner.kind.name
            .lowercase()
    val name: String = connection.owner.client.name
    val clientId: String? = connection.owner.client.clientId
    val clientHost: String? = connection.owner.client.host
    val spaceIds: List<UUID> = connection.grant.spaces.toList()
    val scope: String = connection.grant.scope.wire
    val createdAt: Instant = connection.term.createdAt
    val lastUsedAt: Instant? = connection.term.lastUsedAt
    val expiresAt: Instant = connection.term.expiresAt
    val revokedAt: Instant? = connection.term.revokedAt

    companion object {
        fun from(connection: Connection): ConnectionResponse = ConnectionResponse(connection)
    }
}
