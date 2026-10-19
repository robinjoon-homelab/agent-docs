package xyz.robinjoon.agentdocs.access.application.model

// The fields of a client id metadata document the server relies on.
data class ClientMetadataDocument(
    val clientId: String,
    val clientName: String?,
    val redirectUris: List<String>,
    val tokenEndpointAuthMethod: String?,
)
