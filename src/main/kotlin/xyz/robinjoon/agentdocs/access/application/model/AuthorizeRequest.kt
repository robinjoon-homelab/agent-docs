package xyz.robinjoon.agentdocs.access.application.model

// The authorization endpoint's query parameters as they arrived. Every field may be missing.
data class AuthorizeRequest(
    val responseType: String?,
    val clientId: String?,
    val redirectUri: String?,
    val state: String?,
    val pkce: PkceParameters,
    val target: TargetParameters,
)
