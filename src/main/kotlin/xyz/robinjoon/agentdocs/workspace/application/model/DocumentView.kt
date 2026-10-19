package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.space.Space

data class DocumentView(
    val document: Document,
    val space: Space,
    val marks: List<BlockMark>,
)
