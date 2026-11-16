package xyz.robinjoon.agentdocs.config

import io.micrometer.core.instrument.MeterRegistry
import io.modelcontextprotocol.common.McpTransportContext
import io.modelcontextprotocol.json.McpJsonMapper
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper
import io.modelcontextprotocol.server.McpServer
import io.modelcontextprotocol.server.McpStatelessSyncServer
import io.modelcontextprotocol.server.transport.DefaultServerTransportSecurityValidator
import io.modelcontextprotocol.spec.McpSchema
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStatelessServerTransport
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.web.servlet.function.RouterFunction
import org.springframework.web.servlet.function.ServerResponse
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.agentdocs.access.application.model.UploadToken
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.EditStatisticsRecorder
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
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.DocsTransferTool
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.DocsUploadTool
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.McpInstructions
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.McpSettings
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.McpTool
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.McpToolSpecifications
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.NavigationTool
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.RestoreBlockTool
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.RevisionNameTool
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.SpacesListTool
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.SpacesResolveTool
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.StatsEditEndTool
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp.StatsEditStartTool
import xyz.robinjoon.agentdocs.workspace.application.port.input.ActivityUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.AssetUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentAssetUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentQueryUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentTransferUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.EditSessionUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.NavigationUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.SearchUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.SpaceUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.SuggestionUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.ThreadUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.UploadGrantUseCase
import java.net.URI
import java.time.Duration

// The MCP endpoint and its tools, all calling the same workspace use cases as the REST API. The official MCP Java SDK
// runs the protocol as a stateless Streamable HTTP server: one JSON response per POST, no session and no server push.
@Configuration(proxyBeanMethods = false)
class McpConfig {
    @Bean
    fun mcpSettings(properties: AgentDocsProperties): McpSettings {
        val origin = URI.create(properties.publicUrl).let { "${it.scheme}://${it.authority}" }
        return McpSettings(setOf(origin))
    }

    // The transport and the server read and write JSON-RPC with the application's own JSON settings.
    @Bean
    fun mcpJsonMapper(json: JsonMapper): McpJsonMapper = JacksonMcpJsonMapper(json)

    // Authentication happens before the transport; it passes the authenticated principal on to the tool calls. A
    // browser request from another origin is refused with 403, and requests without an Origin, as CLI clients send, pass.
    @Bean
    fun mcpTransport(
        mapper: McpJsonMapper,
        settings: McpSettings,
    ): WebMvcStatelessServerTransport =
        WebMvcStatelessServerTransport
            .builder()
            .jsonMapper(mapper)
            .messageEndpoint(MCP_ENDPOINT)
            .contextExtractor { request ->
                McpTransportContext.create(request.principal().map { mapOf(McpToolSpecifications.PRINCIPAL to it) }.orElse(emptyMap()))
            }.securityValidator(DefaultServerTransportSecurityValidator.builder().allowedOrigins(settings.allowedOrigins.toList()).build())
            .build()

    @Bean
    fun mcpToolSpecifications(
        json: JsonMapper,
        meters: MeterRegistry,
    ): McpToolSpecifications = McpToolSpecifications(json, meters)

    // Spring hands over every tool bean in the order they are declared below, which is the order tools/list shows.
    // Calls run on the request thread, and an upload from a slow URL may take up to the timeout.
    @Bean
    fun mcpServer(
        transport: WebMvcStatelessServerTransport,
        tools: List<McpTool>,
        specifications: McpToolSpecifications,
        mapper: McpJsonMapper,
    ): McpStatelessSyncServer =
        McpServer
            .sync(transport)
            .serverInfo(McpSchema.Implementation("agent-docs", "Agent Docs", SERVER_VERSION))
            .instructions(McpInstructions.TEXT)
            .capabilities(
                McpSchema.ServerCapabilities
                    .builder()
                    .tools(false)
                    .build(),
            ).tools(tools.map(specifications::of))
            .jsonMapper(mapper)
            .immediateExecution(true)
            .requestTimeout(toolTimeout)
            .build()

    // The server installs itself as the transport's handler when it is built, so the route waits for it.
    @Bean
    @DependsOn("mcpServer")
    fun mcpRoutes(transport: WebMvcStatelessServerTransport): RouterFunction<ServerResponse> = transport.routerFunction

    @Bean
    fun spacesListTool(spaces: SpaceUseCase): McpTool = SpacesListTool(spaces)

    @Bean
    fun spacesResolveTool(spaces: SpaceUseCase): McpTool = SpacesResolveTool(spaces)

    @Bean
    fun docsSearchTool(search: SearchUseCase): McpTool = DocsSearchTool(search)

    @Bean
    fun docsListTool(queries: DocumentQueryUseCase): McpTool = DocsListTool(queries)

    @Bean
    fun docsReadTool(
        queries: DocumentQueryUseCase,
        spaces: SpaceUseCase,
    ): McpTool = DocsReadTool(queries, spaces)

    @Bean
    fun docsCreateTool(
        documents: DocumentUseCase,
        recorder: EditStatisticsRecorder,
    ): McpTool = DocsCreateTool(documents, recorder)

    @Bean
    fun docsEditTool(
        documents: DocumentUseCase,
        recorder: EditStatisticsRecorder,
    ): McpTool = DocsEditTool(documents, recorder)

    @Bean
    fun docsMoveTool(
        documents: DocumentUseCase,
        recorder: EditStatisticsRecorder,
    ): McpTool = DocsMoveTool(documents, recorder)

    @Bean
    fun docsUploadTool(
        assets: AssetUseCase,
        uploads: UploadGrantUseCase,
        properties: AgentDocsProperties,
        documentAssets: DocumentAssetUseCase,
    ): McpTool = DocsUploadTool(assets, uploads, "${properties.publicUrl.trimEnd('/')}${UploadToken.ENDPOINT}", documentAssets)

    @Bean
    fun docsHistoryTool(
        queries: DocumentQueryUseCase,
        documents: DocumentUseCase,
        recorder: EditStatisticsRecorder,
    ): McpTool = DocsHistoryTool(queries, documents, recorder)

    @Bean
    fun commentsQueryTool(
        threads: ThreadUseCase,
        spaces: SpaceUseCase,
    ): McpTool = CommentsQueryTool(threads, spaces)

    @Bean
    fun commentsWriteTool(
        threads: ThreadUseCase,
        suggestions: SuggestionUseCase,
    ): McpTool = CommentsWriteTool(threads, suggestions)

    @Bean
    fun changesSinceTool(activity: ActivityUseCase): McpTool = ChangesSinceTool(activity)

    @Bean
    fun docsTransferTool(
        documents: DocumentTransferUseCase,
        recorder: EditStatisticsRecorder,
    ): McpTool = DocsTransferTool(documents, recorder)

    @Bean
    fun navigationTool(navigation: NavigationUseCase): McpTool = NavigationTool(navigation)

    @Bean
    fun revisionNameTool(documents: DocumentUseCase): McpTool = RevisionNameTool(documents)

    @Bean
    fun restoreBlockTool(
        documents: DocumentUseCase,
        recorder: EditStatisticsRecorder,
    ): McpTool = RestoreBlockTool(documents, recorder)

    @Bean
    fun statsEditStartTool(sessions: EditSessionUseCase): McpTool = StatsEditStartTool(sessions)

    @Bean
    fun statsEditEndTool(sessions: EditSessionUseCase): McpTool = StatsEditEndTool(sessions)

    private companion object {
        const val MCP_ENDPOINT = "/mcp"
        const val SERVER_VERSION = "0.3.0"
        val toolTimeout: Duration = Duration.ofMinutes(1)
    }
}
