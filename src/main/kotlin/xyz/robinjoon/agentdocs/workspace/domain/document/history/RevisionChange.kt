package xyz.robinjoon.agentdocs.workspace.domain.document.history

import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.InvalidDocumentException
import java.time.Instant
import java.util.Collections

// Who changed the document, through which path, and what. A change is an edit unless marked otherwise.
@ConsistentCopyVisibility
data class RevisionChange private constructor(
    val origin: ChangeOrigin,
    val summary: String,
    val changedBlocks: List<BlockId>,
    val period: ChangePeriod,
    val kind: RevisionKind,
) {
    init {
        if (summary.isBlank() || summary.length > MAX_SUMMARY_LENGTH || summary.contains('\n')) {
            throw InvalidDocumentException("a change summary is one line of 1 to $MAX_SUMMARY_LENGTH characters")
        }
    }

    val amendable: Boolean get() = kind == RevisionKind.EDIT

    fun withKind(kind: RevisionKind): RevisionChange = copy(kind = kind)

    // Folds a later save into this change: the blocks either save touched count as changed.
    fun amendedWith(
        later: RevisionChange,
        summary: String,
    ): RevisionChange =
        RevisionChange(
            origin,
            summary,
            snapshot((changedBlocks + later.changedBlocks).distinct()),
            ChangePeriod(period.createdAt, later.period.updatedAt),
            kind,
        )

    companion object {
        const val MAX_SUMMARY_LENGTH = 200

        fun of(
            origin: ChangeOrigin,
            summary: String,
            changedBlocks: List<BlockId>,
            at: Instant,
        ): RevisionChange = RevisionChange(origin, summary.trim(), snapshot(changedBlocks), ChangePeriod(at, at), RevisionKind.EDIT)

        fun restore(
            origin: ChangeOrigin,
            summary: String,
            changedBlocks: List<BlockId>,
            period: ChangePeriod,
        ): RevisionChange = RevisionChange(origin, summary, snapshot(changedBlocks), period, RevisionKind.EDIT)

        private fun snapshot(ids: List<BlockId>): List<BlockId> = Collections.unmodifiableList(ids.toList())
    }
}
