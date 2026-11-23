package xyz.robinjoon.agentdocs.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.EditStatisticsRecorder
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.FlowUpgradeTool
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.McpTool
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase

@Configuration(proxyBeanMethods = false)
class FlowUpgradeConfig {
    @Bean
    fun flowUpgradeTool(
        documents: DocumentUseCase,
        recorder: EditStatisticsRecorder,
    ): McpTool = FlowUpgradeTool(documents, recorder)
}
