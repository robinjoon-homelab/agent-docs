package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.space.Space
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId

data class SearchHit(
    val kind: SearchHitKind,
    val space: Space,
    val document: DocumentRef,
    val blockId: BlockId?,
    val threadId: ThreadId?,
    val snippet: String,
)
