package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.ActorKind
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadStatus
import java.time.Instant

// Every filter is optional. spaces null means every space the caller sees. since keeps threads changed after that
// moment, which is how an agent asks "what is new since I last looked".
data class ThreadQuery(
    val spaces: Set<SpaceId>?,
    val document: DocumentId?,
    val statuses: Set<ThreadStatus>?,
    val authorKind: ActorKind?,
    val mention: String?,
    val since: Instant?,
) {
    companion object {
        const val MAX_RESULTS = 100
    }
}
