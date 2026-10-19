package xyz.robinjoon.agentdocs.access.application.port.output

import xyz.robinjoon.agentdocs.access.application.model.ClientMetadataDocument

interface ClientMetadataSource {
    // Fetches the document at the client id URL without following redirects. Fails with
    // ClientMetadataUnavailableException when it cannot be fetched or parsed.
    fun fetch(clientId: String): ClientMetadataDocument
}
