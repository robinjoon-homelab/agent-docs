package xyz.robinjoon.agentdocs.workspace.application.port.input

import xyz.robinjoon.agentdocs.workspace.application.model.DesignRenderResult
import xyz.robinjoon.agentdocs.workspace.application.model.RenderSelection
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId

fun interface FlowDesignRenderUseCase {
    fun render(
        access: Access,
        document: DocumentId,
        selection: RenderSelection,
    ): DesignRenderResult
}
