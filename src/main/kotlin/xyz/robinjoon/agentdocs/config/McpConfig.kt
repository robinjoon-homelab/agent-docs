package xyz.robinjoon.agentdocs.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.ChangesSinceTool
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.CommentsQueryTool
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.CommentsWriteTool
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.DocsCreateTool
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.DocsEditTool
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.DocsHistoryTool
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.DocsListTool
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.DocsMoveTool
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.DocsReadTool
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.DocsSearchTool
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.DocsUploadTool
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.McpProtocol
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.McpSettings
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.McpTool
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.SpacesListTool
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.SpacesResolveTool
import xyz.robinjoon.agentdocs.workspace.application.port.input.ActivityUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.AssetUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentQueryUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.SearchUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.SpaceUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.ThreadUseCase
import java.net.URI

// The MCP endpoint and its tools, all calling the same workspace use cases as the REST API.
@Configuration(proxyBeanMethods = false)
class McpConfig {
    @Bean
    fun mcpSettings(properties: AgentDocsProperties): McpSettings {
        val origin = URI.create(properties.publicUrl).let { "${it.scheme}://${it.authority}" }
        return McpSettings(setOf(origin), "0.1.0")
    }

    // Spring hands over every tool bean in the order they are declared below, which is the order tools/list shows.
    @Bean
    fun mcpProtocol(
        tools: List<McpTool>,
        json: ObjectMapper,
        settings: McpSettings,
    ): McpProtocol = McpProtocol(tools, json, settings)

    @Bean
    fun spacesListTool(spaces: SpaceUseCase): McpTool = SpacesListTool(spaces)

    @Bean
    fun spacesResolveTool(spaces: SpaceUseCase): McpTool = SpacesResolveTool(spaces)

    @Bean
    fun docsSearchTool(search: SearchUseCase): McpTool = DocsSearchTool(search)

    @Bean
    fun docsListTool(queries: DocumentQueryUseCase): McpTool = DocsListTool(queries)

    @Bean
    fun docsReadTool(queries: DocumentQueryUseCase): McpTool = DocsReadTool(queries)

    @Bean
    fun docsCreateTool(documents: DocumentUseCase): McpTool = DocsCreateTool(documents)

    @Bean
    fun docsEditTool(documents: DocumentUseCase): McpTool = DocsEditTool(documents)

    @Bean
    fun docsMoveTool(documents: DocumentUseCase): McpTool = DocsMoveTool(documents)

    @Bean
    fun docsUploadTool(assets: AssetUseCase): McpTool = DocsUploadTool(assets)

    @Bean
    fun docsHistoryTool(
        queries: DocumentQueryUseCase,
        documents: DocumentUseCase,
    ): McpTool = DocsHistoryTool(queries, documents)

    @Bean
    fun commentsQueryTool(
        threads: ThreadUseCase,
        spaces: SpaceUseCase,
    ): McpTool = CommentsQueryTool(threads, spaces)

    @Bean
    fun commentsWriteTool(threads: ThreadUseCase): McpTool = CommentsWriteTool(threads)

    @Bean
    fun changesSinceTool(activity: ActivityUseCase): McpTool = ChangesSinceTool(activity)
}
