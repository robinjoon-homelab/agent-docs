package xyz.robinjoon.agentdocs.workspace.application.port.output

import xyz.robinjoon.agentdocs.workspace.application.model.DesignRenderInput
import xyz.robinjoon.agentdocs.workspace.application.model.DesignRenderResult

fun interface FlowDesignRenderer {
    fun render(input: DesignRenderInput): DesignRenderResult
}
