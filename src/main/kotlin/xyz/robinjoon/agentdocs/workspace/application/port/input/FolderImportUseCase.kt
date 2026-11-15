package xyz.robinjoon.agentdocs.workspace.application.port.input

import xyz.robinjoon.agentdocs.workspace.application.model.FolderFile
import xyz.robinjoon.agentdocs.workspace.application.model.FolderImportResult
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug

interface FolderImportUseCase {
    fun importFolder(
        context: WriteContext,
        space: SpaceSlug,
        tool: String,
        files: List<FolderFile>,
    ): FolderImportResult
}
