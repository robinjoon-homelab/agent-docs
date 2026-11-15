package xyz.robinjoon.agentdocs.workspace.application.port.input

import xyz.robinjoon.agentdocs.workspace.application.model.DocumentTransfer
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentWrite
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId

interface DocumentTransferUseCase {
    fun transfer(
        context: WriteContext,
        id: DocumentId,
        request: DocumentTransfer,
    ): DocumentWrite
}
