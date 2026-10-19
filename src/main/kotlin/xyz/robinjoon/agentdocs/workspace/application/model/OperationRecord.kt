package xyz.robinjoon.agentdocs.workspace.application.model

import java.util.UUID

// What a write with an operation id produced, so a retry can answer with the first result. target is the document,
// thread, comment, or asset the write created or changed; revision is set for document writes.
data class OperationRecord(
    val kind: String,
    val target: UUID,
    val revision: Int?,
)
