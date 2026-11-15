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
            val current = documents.findById(id)?.takeIf { access.sees(it.spaceId) } ?: throw DocumentNotFoundException(root)
            Permissions.require(access, current.spaceId, Permission.EDIT, "editing a document")
        }
        if (record.state == OperationResultState.REMOVED) throw ResultRemovedException(record)
        return record.documentWrite ?: throw LegacyOperationException(record)
    }
}
