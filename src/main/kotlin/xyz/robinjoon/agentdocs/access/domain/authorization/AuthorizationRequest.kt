package xyz.robinjoon.agentdocs.access.domain.authorization

import xyz.robinjoon.agentdocs.access.domain.TokenHash
import xyz.robinjoon.agentdocs.domain.AggregateRoot
import java.time.Duration
import java.time.Instant

// One authorization attempt from the approval screen to the token exchange. It waits ten minutes for the owner, and
// an approved code is valid for ten minutes and exactly one exchange.
@AggregateRoot
@ConsistentCopyVisibility
data class AuthorizationRequest private constructor(
    val id: AuthorizationRequestId,
    val ask: AuthorizationAsk,
    val decision: AuthorizationDecision,
    val expiresAt: Instant,
) {
    fun isOpenAt(at: Instant): Boolean = decision == AuthorizationDecision.Pending && at < expiresAt

    fun approve(
        approval: Approval,
        at: Instant,
    ): AuthorizationRequest {
        if (!isOpenAt(
                at,
            )
        ) {
            throw AuthorizationFailedException("access_denied", "the authorization request is no longer waiting for approval")
        }
        return copy(decision = AuthorizationDecision.Approved(approval), expiresAt = at.plus(lifetime))
    }

    fun deny(at: Instant): AuthorizationRequest {
        if (!isOpenAt(
                at,
            )
        ) {
            throw AuthorizationFailedException("access_denied", "the authorization request is no longer waiting for approval")
        }
        return copy(decision = AuthorizationDecision.Denied)
    }

    // Checks everything the token request must repeat from the authorization request, and the PKCE verifier.
    fun exchange(
        exchange: CodeExchange,
        at: Instant,
    ): Pair<AuthorizationRequest, Approval> {
        val approved = decision as? AuthorizationDecision.Approved ?: throw invalidGrant("the code was already used or never approved")
        when {
            at >= expiresAt -> throw invalidGrant("the code expired")
            approved.approval.codeHash != exchange.codeHash -> throw invalidGrant("the code does not match")
            exchange.clientId != ask.clientId -> throw invalidGrant("the code was issued to another client")
            exchange.redirectUri != ask.redirectUri -> throw invalidGrant("redirect_uri differs from the authorization request")
            !ask.challenge.matches(exchange.verifier) -> throw invalidGrant("code_verifier does not match the code_challenge")
        }
        return copy(decision = AuthorizationDecision.Exchanged) to approved.approval
    }

    private fun invalidGrant(description: String) = AuthorizationFailedException("invalid_grant", description)

    companion object {
        private val lifetime: Duration = Duration.ofMinutes(10)

        fun start(
            id: AuthorizationRequestId,
            ask: AuthorizationAsk,
            at: Instant,
        ): AuthorizationRequest = AuthorizationRequest(id, ask, AuthorizationDecision.Pending, at.plus(lifetime))

        fun restore(
            id: AuthorizationRequestId,
            ask: AuthorizationAsk,
            decision: AuthorizationDecision,
            expiresAt: Instant,
        ): AuthorizationRequest = AuthorizationRequest(id, ask, decision, expiresAt)
    }
}
