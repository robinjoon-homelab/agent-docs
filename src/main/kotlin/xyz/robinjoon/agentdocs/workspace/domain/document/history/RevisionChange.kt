package xyz.robinjoon.agentdocs.workspace.domain.document.history

import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.InvalidDocumentException
import java.time.Instant
import java.util.Collections

// Who changed the document, through which path, and what.
@ConsistentCopyVisibility
data class RevisionChange private constructor(
    val origin: ChangeOrigin,
    val summary: String,
    val changedBlocks: List<BlockId>,
    val period: ChangePeriod,
) {
    init {
        if (summary.isBlank() || summary.length > MAX_SUMMARY_LENGTH || summary.contains('\n')) {
            throw InvalidDocumentException("a change summary is one line of 1 to $MAX_SUMMARY_LENGTH characters")
        }
    }

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
        )

    companion object {
        const val MAX_SUMMARY_LENGTH = 200

        fun of(
            origin: ChangeOrigin,
            summary: String,
            changedBlocks: List<BlockId>,
            at: Instant,
        ): RevisionChange = RevisionChange(origin, summary.trim(), snapshot(changedBlocks), ChangePeriod(at, at))

        fun restore(
            origin: ChangeOrigin,
            summary: String,
            changedBlocks: List<BlockId>,
            period: ChangePeriod,
        ): RevisionChange = RevisionChange(origin, summary, snapshot(changedBlocks), period)

        private fun snapshot(ids: List<BlockId>): List<BlockId> = Collections.unmodifiableList(ids.toList())
    }
}
