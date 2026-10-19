package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.McpToolResult
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WorkspaceJson
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceLocator
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.input.SpaceUseCase

class SpacesListTool(
    private val spaces: SpaceUseCase,
) : McpTool {
    override val definition =
        ToolDefinition(
            name = "spaces_list",
            title = "List spaces",
            description =
                "The spaces this connection may see, with descriptions and the repositories or directories linked to them. A map for " +
                    "the start of a session.",
            properties = emptyMap(),
            required = emptyList(),
            readOnly = true,
        )

    override fun call(
        arguments: Arguments,
        context: WriteContext,
    ): McpToolResult = McpToolResult.data(mapOf("spaces" to spaces.list(context.access).map(WorkspaceJson::overview)))
}

class SpacesResolveTool(
    private val spaces: SpaceUseCase,
) : McpTool {
    override val definition =
        ToolDefinition(
            name = "spaces_resolve",
            title = "Find the space of this repository",
            description =
                "Finds the space linked to the repository or directory you work in. Pass remote from `git remote get-url origin` " +
                    "and/or path from `pwd`. If no single space matches, the error lists the candidates.",
            properties =
                mapOf(
                    "remote" to Schemas.string("Git remote URL, such as git@github.com:owner/repo.git"),
                    "path" to Schemas.string("Absolute local directory path"),
                ),
            required = emptyList(),
            readOnly = true,
        )

    override fun call(
        arguments: Arguments,
        context: WriteContext,
    ): McpToolResult {
        val space = spaces.resolve(context.access, SpaceLocator(arguments.optionalString("remote"), arguments.optionalString("path")))
        return McpToolResult.data(mapOf("space" to WorkspaceJson.space(space)))
    }
}
