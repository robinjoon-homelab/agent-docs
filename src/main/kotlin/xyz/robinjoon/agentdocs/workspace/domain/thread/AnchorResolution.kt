package xyz.robinjoon.agentdocs.workspace.domain.thread

// Where an anchor sits in the current document. A detached thread keeps its quote and stays listed.
sealed interface AnchorResolution {
    data class Attached(
        val start: Int?,
        val end: Int?,
    ) : AnchorResolution

    data object Detached : AnchorResolution
}
