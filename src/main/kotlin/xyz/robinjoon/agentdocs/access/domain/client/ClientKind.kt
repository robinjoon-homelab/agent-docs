package xyz.robinjoon.agentdocs.access.domain.client

enum class ClientKind {
    // The client id is an https URL whose document the server fetched and checked (Client ID Metadata Document).
    METADATA_DOCUMENT,

    // Registered through dynamic client registration, kept for clients that do not publish a metadata document.
    REGISTERED,
}
