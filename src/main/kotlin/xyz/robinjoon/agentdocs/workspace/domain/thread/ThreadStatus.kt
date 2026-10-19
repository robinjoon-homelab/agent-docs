package xyz.robinjoon.agentdocs.workspace.domain.thread

// OPEN → ANSWERED (someone handled it and replied) → RESOLVED (the person who asked agrees). A resolved thread can be
// opened again.
enum class ThreadStatus {
    OPEN,
    ANSWERED,
    RESOLVED,
}
