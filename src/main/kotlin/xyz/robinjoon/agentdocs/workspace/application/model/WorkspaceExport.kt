package xyz.robinjoon.agentdocs.workspace.application.model

data class WorkspaceExport(
    val records: Map<String, List<Map<String, Any?>>>,
    val markdown: Map<String, String>,
    val assets: List<ExportAsset>,
)

data class ExportAsset(
    val id: String,
    val extension: String,
    val sha256: String,
)
