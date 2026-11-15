package xyz.robinjoon.agentdocs.workspace.application.port.output

import xyz.robinjoon.agentdocs.workspace.application.model.OperationKey
import xyz.robinjoon.agentdocs.workspace.application.model.OperationRecord
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentId
import java.time.Instant

interface OperationStore {
    fun find(key: OperationKey): OperationRecord?

    // Remove whole copied results while retaining operation identity and success evidence.
    fun removeCommentResults(comment: CommentId)

    fun removeDocumentResults(documents: List<DocumentId>)

    fun save(
        key: OperationKey,
        record: OperationRecord,
        at: Instant,
    )
}
