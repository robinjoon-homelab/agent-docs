package xyz.robinjoon.agentdocs.workspace.application.service

import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.domain.document.ChangeSource
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.history.RevisionKind
import java.time.Instant

// Whether a write can continue pending web input, and the snapshot kind for immediate confirmation.
internal enum class WriteMode(
    val amendable: Boolean,
    val kind: RevisionKind,
) {
    // A person's body or header input.
    TYPING(true, RevisionKind.EDIT),

    // A guarded block edit confirms immediately.
    EDIT(false, RevisionKind.EDIT),
    RESTORE(false, RevisionKind.RESTORE),
    ;

    fun groupsInput(context: WriteContext): Boolean = amendable && context.source == ChangeSource.WEB && !context.access.actor.isAgent
}

// A move into or out of the trash. kind names the activity log entry and operation the write remembered for a retry.
internal enum class Transition(
    val kind: String,
    val operation: String,
    val summary: String,
) {
    TRASH("document.trashed", "document.trash", "휴지통으로 이동") {
        override fun apply(
            document: Document,
            at: Instant,
        ): Document? = document.trash(at)
    },
    UNTRASH("document.untrashed", "document.untrash", "휴지통에서 복원") {
        override fun apply(
            document: Document,
            at: Instant,
        ): Document? = document.untrash(at)
    },
    ;

    abstract fun apply(
        document: Document,
        at: Instant,
    ): Document?
}
