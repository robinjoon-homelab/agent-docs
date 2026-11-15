package xyz.robinjoon.agentdocs.workspace.application.port.output

import xyz.robinjoon.agentdocs.workspace.application.model.PersonalDocument
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import java.time.Instant
import java.util.UUID

interface PersonalDocumentStore {
    fun list(userId: UUID): List<PersonalDocument>

    fun recordView(
        userId: UUID,
        documentId: DocumentId,
        at: Instant,
    )

    fun favorite(
        userId: UUID,
        documentId: DocumentId,
        at: Instant?,
    )
}
