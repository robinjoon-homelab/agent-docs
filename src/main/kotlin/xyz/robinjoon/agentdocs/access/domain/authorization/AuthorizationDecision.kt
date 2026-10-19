package xyz.robinjoon.agentdocs.access.domain.authorization

sealed interface AuthorizationDecision {
    data object Pending : AuthorizationDecision

    data class Approved(
        val approval: Approval,
    ) : AuthorizationDecision

    data object Denied : AuthorizationDecision

    // The code was exchanged; a second exchange of the same code fails.
    data object Exchanged : AuthorizationDecision
}
