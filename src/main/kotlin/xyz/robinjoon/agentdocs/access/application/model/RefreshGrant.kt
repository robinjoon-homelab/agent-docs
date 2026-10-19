package xyz.robinjoon.agentdocs.access.application.model

data class RefreshGrant(
    val clientId: String?,
    val refreshToken: String?,
    val resources: List<String>,
)
