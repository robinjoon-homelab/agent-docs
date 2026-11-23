package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowDesignBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.design.DesignAddress

data class RenderSelection(
    val screen: BlockId,
    val state: BlockId?,
    val target: DesignAddress?,
    val mode: String,
    val image: Boolean,
) {
    init {
        if (mode !in setOf("viewport", "full")) throw InvalidRequestException("Render mode must be viewport or full")
    }
}

data class DesignRenderInput(
    val blocks: List<FlowDesignBlock>,
    val screen: String,
    val state: String?,
    val selection: RenderSelection,
    val assets: Map<String, String>,
)

data class DesignRenderResult(
    val measurements: Map<String, Any?>,
    val png: String?,
)

class RenderUnavailableException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
