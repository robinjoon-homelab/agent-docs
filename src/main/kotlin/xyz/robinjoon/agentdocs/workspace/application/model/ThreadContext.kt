package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug

// The anchored block's current annotated markdown and one block before and after it. Each is null when absent.
data class ThreadContext(
    val space: SpaceSlug,
    val path: DocumentPath,
    val title: String,
    val anchorBlock: String?,
    val previousBlock: String?,
    val nextBlock: String?,
)
