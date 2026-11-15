package xyz.robinjoon.agentdocs.workspace.application.model

import java.util.UUID

// What a write with an operation id produced, so a retry can answer with the first result. target is the document,
// thread, comment, or asset the write created or changed; revision is set for document writes.
data class OperationRecord
    // quality-exception: 멱등 결과는 기존 문서·댓글 응답과 리비전 이름 결과를 함께 구분해 보존한다.
    @Suppress("LongParameterList")
    constructor(
        val kind: String,
        val target: UUID,
        val revision: Int?,
        val documentWrite: DocumentWrite? = null,
        val threadView: ThreadView? = null,
        val state: OperationResultState =
            if (documentWrite != null || threadView != null) OperationResultState.AVAILABLE else OperationResultState.LEGACY,
        val revisionLabel: RevisionLabel? = null,
    )

// Removal keeps proof of success without keeping deleted content or permitting the write again.
enum class OperationResultState {
    LEGACY,
    AVAILABLE,
    REMOVED,
    COMPLETED,
}
