package xyz.robinjoon.agentdocs.access.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import xyz.robinjoon.agentdocs.access.application.port.output.AuthorizationRequestRepository
import xyz.robinjoon.agentdocs.access.domain.TokenHash
import xyz.robinjoon.agentdocs.access.domain.authorization.Approval
import xyz.robinjoon.agentdocs.access.domain.authorization.AuthorizationAsk
import xyz.robinjoon.agentdocs.access.domain.authorization.AuthorizationDecision
import xyz.robinjoon.agentdocs.access.domain.authorization.AuthorizationRequest
import xyz.robinjoon.agentdocs.access.domain.authorization.AuthorizationRequestId
import xyz.robinjoon.agentdocs.access.domain.authorization.PkceChallenge
import xyz.robinjoon.agentdocs.access.domain.connection.Grant
import xyz.robinjoon.agentdocs.access.domain.user.UserId
import java.time.Duration
import java.time.ZoneOffset
import java.util.UUID

// The approval is kept after the exchange too, so a used code still names the code hash and cannot be reused.
class ExposedAuthorizationRequestRepository : AuthorizationRequestRepository {
    override fun add(request: AuthorizationRequest): AuthorizationRequest {
        AuthorizationRequestsTable.insert {
            it[id] = request.id.value
            it[clientId] = request.ask.clientId
            it[redirectUri] = request.ask.redirectUri
            it[clientState] = request.ask.state
            it[codeChallenge] = request.ask.challenge.value
            it[resourceUri] = request.ask.resource
            it[requestedScope] = request.ask.requestedScope
            write(it, request)
        }
        return requireNotNull(findById(request.id))
    }

    override fun update(request: AuthorizationRequest): AuthorizationRequest {
        val updated = AuthorizationRequestsTable.update({ AuthorizationRequestsTable.id eq request.id.value }) { write(it, request) }
        check(updated == 1) { "Authorization request to update does not exist: ${request.id}" }
        return requireNotNull(findById(request.id))
    }

    override fun findById(id: AuthorizationRequestId): AuthorizationRequest? = byId(id).singleOrNull()?.toRequest()

    override fun findByIdForUpdate(id: AuthorizationRequestId): AuthorizationRequest? = byId(id).forUpdate().singleOrNull()?.toRequest()

    private fun byId(id: AuthorizationRequestId) =
        AuthorizationRequestsTable.selectAll().where { AuthorizationRequestsTable.id eq id.value }

    override fun findByCodeForUpdate(hash: TokenHash): AuthorizationRequest? =
        AuthorizationRequestsTable
            .selectAll()
            .where { AuthorizationRequestsTable.codeHash eq hash.value }
            .forUpdate()
            .singleOrNull()
            ?.toRequest()

    private fun write(
        statement: UpdateBuilder<*>,
        request: AuthorizationRequest,
    ) {
        val decision = request.decision
        val approval = (decision as? AuthorizationDecision.Approved)?.approval
        statement[AuthorizationRequestsTable.decision] = nameOf(decision)
        statement[AuthorizationRequestsTable.expiresAt] = request.expiresAt.atOffset(ZoneOffset.UTC)
        if (approval != null) {
            statement[AuthorizationRequestsTable.approverId] = approval.userId.value
            statement[AuthorizationRequestsTable.grantedScope] = approval.grant.scope
            statement[AuthorizationRequestsTable.grantedSpaces] = approval.grant.spaces.joinToString(" ")
            statement[AuthorizationRequestsTable.lifetimeSeconds] = approval.lifetime.seconds
            statement[AuthorizationRequestsTable.codeHash] = approval.codeHash.value
        }
    }

    private fun nameOf(decision: AuthorizationDecision): String =
        when (decision) {
            AuthorizationDecision.Pending -> PENDING
            is AuthorizationDecision.Approved -> APPROVED
            AuthorizationDecision.Denied -> DENIED
            AuthorizationDecision.Exchanged -> EXCHANGED
        }

    private fun ResultRow.toRequest(): AuthorizationRequest {
        val ask =
            AuthorizationAsk(
                this[AuthorizationRequestsTable.clientId],
                this[AuthorizationRequestsTable.redirectUri],
                this[AuthorizationRequestsTable.clientState],
                PkceChallenge(this[AuthorizationRequestsTable.codeChallenge]),
                this[AuthorizationRequestsTable.resourceUri],
                this[AuthorizationRequestsTable.requestedScope],
            )
        val decision =
            when (this[AuthorizationRequestsTable.decision]) {
                APPROVED -> AuthorizationDecision.Approved(approvalOf(this))
                DENIED -> AuthorizationDecision.Denied
                EXCHANGED -> AuthorizationDecision.Exchanged
                else -> AuthorizationDecision.Pending
            }
        return AuthorizationRequest.restore(
            AuthorizationRequestId(this[AuthorizationRequestsTable.id]),
            ask,
            decision,
            this[AuthorizationRequestsTable.expiresAt].toInstant(),
        )
    }

    private fun approvalOf(row: ResultRow): Approval {
        val spaces =
            requireNotNull(
                row[AuthorizationRequestsTable.grantedSpaces],
            ).split(' ').filter(String::isNotEmpty).map(UUID::fromString)
        return Approval(
            UserId(requireNotNull(row[AuthorizationRequestsTable.approverId])),
            Grant.of(spaces.toSet(), requireNotNull(row[AuthorizationRequestsTable.grantedScope])),
            Duration.ofSeconds(requireNotNull(row[AuthorizationRequestsTable.lifetimeSeconds])),
            TokenHash(requireNotNull(row[AuthorizationRequestsTable.codeHash])),
        )
    }

    private companion object {
        const val PENDING = "PENDING"
        const val APPROVED = "APPROVED"
        const val DENIED = "DENIED"
        const val EXCHANGED = "EXCHANGED"
    }
}
