package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.editsession.HumanInputBatch
import xyz.robinjoon.agentdocs.workspace.domain.editsession.InputTrace

// Produced only after the document write committed; changed is the write result, never a client claim.
data class SavedEditInput(
    val documentId: DocumentId,
    val version: Int,
    val changed: Boolean,
    val batch: HumanInputBatch,
) {
    constructor(documentId: DocumentId, version: Int, changed: Boolean, trace: InputTrace) :
        this(documentId, version, changed, HumanInputBatch.single(trace))
}
