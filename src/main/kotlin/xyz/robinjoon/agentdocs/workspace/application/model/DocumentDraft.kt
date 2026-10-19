package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentProperties

data class DocumentDraft(
    val path: DocumentPath,
    val title: String,
    val properties: DocumentProperties,
    val blocks: List<Block>,
)
