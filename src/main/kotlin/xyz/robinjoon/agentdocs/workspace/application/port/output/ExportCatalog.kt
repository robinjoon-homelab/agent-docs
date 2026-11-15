package xyz.robinjoon.agentdocs.workspace.application.port.output

import xyz.robinjoon.agentdocs.workspace.application.model.WorkspaceExport
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug

// Unbounded read projection. Includes trash, archived spaces, confirmed history, and pending rows without finalizing.
interface ExportCatalog {
    fun snapshot(space: SpaceSlug?): WorkspaceExport
}
