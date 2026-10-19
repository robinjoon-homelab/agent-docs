package xyz.robinjoon.agentdocs.access.application.model

// The client's metadata document could not be fetched or is not valid JSON.
class ClientMetadataUnavailableException(
    clientId: String,
    cause: Throwable?,
) : RuntimeException("the client metadata document at $clientId could not be read", cause)
