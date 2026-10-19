package xyz.robinjoon.agentdocs.access.application.model

import xyz.robinjoon.agentdocs.access.domain.authorization.AuthorizationRequest
import xyz.robinjoon.agentdocs.access.domain.client.OAuthClient

data class AuthorizationView(
    val request: AuthorizationRequest,
    val client: OAuthClient,
)
