package xyz.robinjoon.agentdocs.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.EditStatisticsRecorder
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.FlowDesignActions
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.FlowDesignPatchTool
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.FlowDesignReadTool
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.McpTool
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentQueryUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase

@Configuration(proxyBeanMethods = false)
class FlowDesignToolsConfig {
    @Bean
    fun flowDesignActions(
        queries: DocumentQueryUseCase,
        documents: DocumentUseCase,
        recorder: EditStatisticsRecorder,
        properties: AgentDocsProperties,
    ): FlowDesignActions = FlowDesignActions(queries, documents, recorder, properties.publicUrl)

    @Bean
    fun flowDesignReadTool(actions: FlowDesignActions): McpTool = FlowDesignReadTool(actions)

    @Bean
    fun flowDesignPatchTool(actions: FlowDesignActions): McpTool = FlowDesignPatchTool(actions)
}
