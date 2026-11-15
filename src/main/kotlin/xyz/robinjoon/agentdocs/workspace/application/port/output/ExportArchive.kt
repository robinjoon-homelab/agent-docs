package xyz.robinjoon.agentdocs.workspace.application.port.output

import xyz.robinjoon.agentdocs.workspace.application.model.WorkspaceExport

interface ExportArchive {
    fun zip(snapshot: WorkspaceExport): ByteArray
}
