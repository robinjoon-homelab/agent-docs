package xyz.robinjoon.agentdocs.workspace.application.port.output

import xyz.robinjoon.agentdocs.workspace.application.model.DocumentEventSnapshot
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId

// Projection only, never loads or finalizes document content.
interface DocumentEventCatalog {
    fun snapshot(id: DocumentId): DocumentEventSnapshot?
}
