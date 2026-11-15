package xyz.robinjoon.agentdocs.workspace.application.port.input

import xyz.robinjoon.agentdocs.workspace.application.model.DocumentEventSnapshot
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId

interface DocumentEventsUseCase {
    fun snapshot(
        access: Access,
        id: DocumentId,
    ): DocumentEventSnapshot
}
