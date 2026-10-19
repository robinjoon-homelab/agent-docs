package xyz.robinjoon.agentdocs.access.application.model

// A token request with grant_type=authorization_code. resources are every resource parameter sent; repeats of the
// same value are accepted because some clients send it twice.
data class CodeGrant(
    val clientId: String?,
    val code: String?,
    val verifier: String?,
    val redirectUri: String?,
    val resources: List<String>,
)
