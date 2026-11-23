package xyz.robinjoon.agentdocs.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.FlowDesignRenderTool
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.McpTool
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.render.ChromiumDesignRenderer
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentAssetUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentQueryUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.FlowDesignRenderUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.output.FlowDesignRenderer
import xyz.robinjoon.agentdocs.workspace.application.service.FlowDesignRenderService

@Configuration(proxyBeanMethods = false)
class FlowDesignRenderConfig {
    @Bean
    fun chromiumDesignRenderer(
        @Value("\${agent-docs.render.chromium:/usr/bin/chromium}") executable: String,
        json: ObjectMapper,
    ): FlowDesignRenderer = ChromiumDesignRenderer(executable, json)

    @Bean
    fun flowDesignRenderService(
        queries: DocumentQueryUseCase,
        assets: DocumentAssetUseCase,
        renderer: FlowDesignRenderer,
    ): FlowDesignRenderUseCase = FlowDesignRenderService(queries, assets, renderer)

    @Bean
    fun flowDesignRenderTool(renders: FlowDesignRenderUseCase): McpTool = FlowDesignRenderTool(renders)
}
