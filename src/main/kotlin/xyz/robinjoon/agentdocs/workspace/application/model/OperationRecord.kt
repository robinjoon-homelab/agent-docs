package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import java.util.UUID

// What a write with an operation id produced, so a retry can answer with the first result. target is the document,
// thread, comment, or asset the write created or changed; revision is set for document writes.
data class OperationRecord
    // quality-exception: 멱등 결과는 문서·댓글·첨부·리비전 이름 응답을 구분하고, 삭제된 문서는 권한 확인에 필요한 소속만 보존한다.
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
        val purgedFrom: SpaceId? = null,
        val assetAlt: String? = null,
    )

// Removal keeps proof of success without keeping deleted content or permitting the write again.
enum class OperationResultState {
    LEGACY,
    AVAILABLE,
    REMOVED,
    COMPLETED,
}
