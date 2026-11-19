package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.document.Block

data class WorkspaceExport(
    val records: Map<String, List<Map<String, Any?>>>,
    val documents: Map<String, ExportDocument>,
    val assets: List<ExportAsset>,
)

data class ExportDocument(
    val title: String,
    val blocks: List<Block>,
)

data class ExportAsset(
    val id: String,
    val extension: String,
    val sha256: String,
)
