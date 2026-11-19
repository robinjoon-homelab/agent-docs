package xyz.robinjoon.agentdocs.workspace.application.service

import xyz.robinjoon.agentdocs.workspace.application.model.DocumentWrite
import xyz.robinjoon.agentdocs.workspace.application.model.OperationRecord
import xyz.robinjoon.agentdocs.workspace.application.model.OperationResultState
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.LegacyOperationException
import xyz.robinjoon.agentdocs.workspace.application.port.input.ResultRemovedException
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentRepository
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.Permission
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId

internal class DocumentReplay(
    private val documents: DocumentRepository,
) {
    fun result(
        access: Access,
        record: OperationRecord,
    ): DocumentWrite {
        val root = DocumentId(record.target)
        val ids =
            (
                listOf(root) +
                    record.documentWrite
                        ?.related
                        .orEmpty()
                        .map { it.document.id }
            ).distinct()
        ids.forEach { id ->
            requireEditable(access, record, id)
        }
        return record.documentWrite ?: throw LegacyOperationException(record)
    }

    fun requireEditable(
        access: Access,
        record: OperationRecord,
        id: DocumentId,
    ) {
        val root = DocumentId(record.target)
        val current = documents.findById(id)
        val removed = record.state == OperationResultState.REMOVED
        if (current == null && removed && id == root && access.isOwner) throw ResultRemovedException(record)
        val space = current?.spaceId ?: record.purgedFrom?.takeIf { removed && id == root }
        if (space == null || !access.sees(space)) throw DocumentNotFoundException(root)
        Permissions.require(access, space, Permission.EDIT, "editing a document")
        if (removed) throw ResultRemovedException(record)
    }
}
