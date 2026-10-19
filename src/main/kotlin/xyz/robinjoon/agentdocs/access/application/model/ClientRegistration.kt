package xyz.robinjoon.agentdocs.access.application.model

// A dynamic client registration request (RFC 7591). Only public clients are registered.
data class ClientRegistration(
    val clientName: String?,
    val redirectUris: List<String>,
    val tokenEndpointAuthMethod: String?,
    val grantTypes: List<String>?,
)
