package xyz.robinjoon.agentdocs.workspace.application.service

import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.history.RevisionKind
import java.time.Instant

// How a write lands in history: whether it may fold into the person's latest edit, and the kind of revision it
// adds otherwise.
internal enum class WriteMode(
    val amendable: Boolean,
    val kind: RevisionKind,
) {
    // A person's save on the latest revision, or their header change.
    TYPING(true, RevisionKind.EDIT),

    // An agent's edit, or a save merged over someone else's change.
    EDIT(false, RevisionKind.EDIT),
    RESTORE(false, RevisionKind.RESTORE),
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
