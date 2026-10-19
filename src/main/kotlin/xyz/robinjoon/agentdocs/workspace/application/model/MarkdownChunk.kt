package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.document.Document

// One response worth of a document. nextCursor is set when more blocks remain; pass it back to read on.
data class MarkdownChunk(
    val document: Document,
    val text: String,
    val nextCursor: Int?,
    val totalBlocks: Int,
)
