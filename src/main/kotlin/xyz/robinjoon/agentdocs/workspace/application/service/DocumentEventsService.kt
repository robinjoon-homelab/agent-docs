package xyz.robinjoon.agentdocs.workspace.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentEventSnapshot
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentEventsUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentEventCatalog
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.Permission
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId

@Transactional(readOnly = true)
class DocumentEventsService(
    private val catalog: DocumentEventCatalog,
) : DocumentEventsUseCase {
    override fun snapshot(
        access: Access,
        id: DocumentId,
    ): DocumentEventSnapshot {
        val snapshot =
            catalog.snapshot(id)?.takeIf { access.sees(it.spaceId) }
                ?: throw DocumentNotFoundException(id)
        Permissions.require(access, snapshot.spaceId, Permission.READ, "watch document")
        return snapshot
    }
}
