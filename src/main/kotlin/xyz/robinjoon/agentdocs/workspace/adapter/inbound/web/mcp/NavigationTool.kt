package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.McpToolResult
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.NavigationJson
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.input.NavigationUseCase
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId

class NavigationTool(
    private val navigation: NavigationUseCase,
) : McpTool {
    override val definition =
        ToolDefinition(
            name = "docs_navigation",
            title = "Personal navigation",
            description =
                "recent/favorites list your documents; backlinks lists accessible source documents. " +
                    "view explicitly records a visit; favorite/unfavorite sets a bookmark. Reads never record visits.",
            properties =
                mapOf(
                    "action" to Schemas.choice("Action", listOf("recent", "favorites", "backlinks", "view", "favorite", "unfavorite")),
                    "document" to Schemas.string("Document id for backlinks or mutation"),
                    "limit" to Schemas.integer("Maximum documents, 1..1000, default 20"),
                ),
            required = listOf("action"),
            readOnly = false,
        )

    override fun call(
        arguments: Arguments,
        context: WriteContext,
    ): McpToolResult {
        val access = context.access
        val limit = arguments.optionalInt("limit") ?: 20
        val action = arguments.string("action")
        if (action == "recent") return McpToolResult.data(NavigationJson.page(navigation.recent(access, limit)))
        if (action == "favorites") return McpToolResult.data(NavigationJson.page(navigation.favorites(access, limit)))
        val id = DocumentId(arguments.uuid("document"))
        when (action) {
            "backlinks" -> return McpToolResult.data(NavigationJson.page(navigation.backlinks(access, id, limit)))
            "view" -> navigation.recordView(access, id)
            "favorite" -> navigation.favorite(access, id, true)
            "unfavorite" -> navigation.favorite(access, id, false)
            else -> throw InvalidRequestException("unknown navigation action")
        }
        return McpToolResult.data(mapOf("saved" to true))
    }
}
