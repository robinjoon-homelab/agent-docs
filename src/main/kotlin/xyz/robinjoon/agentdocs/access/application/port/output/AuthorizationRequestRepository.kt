package xyz.robinjoon.agentdocs.access.application.port.output

import xyz.robinjoon.agentdocs.access.domain.TokenHash
import xyz.robinjoon.agentdocs.access.domain.authorization.AuthorizationRequest
import xyz.robinjoon.agentdocs.access.domain.authorization.AuthorizationRequestId

interface AuthorizationRequestRepository {
    fun add(request: AuthorizationRequest): AuthorizationRequest

    fun update(request: AuthorizationRequest): AuthorizationRequest

    fun findById(id: AuthorizationRequestId): AuthorizationRequest?

    // Locks the request, so an approval and a denial cannot both decide it.
    fun findByIdForUpdate(id: AuthorizationRequestId): AuthorizationRequest?

    // Locks the request, so one code is exchanged at most once even when two token requests race.
    fun findByCodeForUpdate(hash: TokenHash): AuthorizationRequest?
}
