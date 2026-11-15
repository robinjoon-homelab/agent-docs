package xyz.robinjoon.agentdocs.workspace.application.port.input

import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug

interface ExportUseCase {
    fun export(
        access: Access,
        space: SpaceSlug?,
    ): ByteArray
}
