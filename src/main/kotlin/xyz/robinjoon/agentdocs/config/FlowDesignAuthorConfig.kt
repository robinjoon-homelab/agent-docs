package xyz.robinjoon.agentdocs.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.EditStatisticsRecorder
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.FlowDesignAuthorActions
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.FlowDesignAuthorTool
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.McpTool
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentQueryUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase

@Configuration(proxyBeanMethods = false)
class FlowDesignAuthorConfig {
    @Bean
    fun flowDesignAuthorActions(
        queries: DocumentQueryUseCase,
        documents: DocumentUseCase,
        recorder: EditStatisticsRecorder,
        properties: AgentDocsProperties,
    ): FlowDesignAuthorActions = FlowDesignAuthorActions(queries, documents, recorder, properties.publicUrl)

    @Bean
    fun flowDesignAuthorTool(actions: FlowDesignAuthorActions): McpTool = FlowDesignAuthorTool(actions)
}
