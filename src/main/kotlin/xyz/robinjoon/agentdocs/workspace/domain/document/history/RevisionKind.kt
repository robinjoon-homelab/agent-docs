package xyz.robinjoon.agentdocs.workspace.domain.document.history

// What made a revision. Only an edit may take in the same person's next save while they keep typing; a restore or a
// trash move marks a point in history that stays as it was.
enum class RevisionKind {
    EDIT,
    RESTORE,
    TRANSITION,
}
