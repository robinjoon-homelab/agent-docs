package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.McpToolResult
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.Wire
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WorkspaceJson
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.RevisionFilter
import xyz.robinjoon.agentdocs.workspace.application.model.UploadedFile
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.input.AssetUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentQueryUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import java.util.Base64

class DocsHistoryTool(
    private val queries: DocumentQueryUseCase,
    private val documents: DocumentUseCase,
) : McpTool {
    override val definition =
        ToolDefinition(
            name = "docs_history",
            title = "Document history",
            description =
                "action list: revisions newest first. action diff: changes from revision from to revision to (default current), with a " +
                    "unified " +
                    "diff whose hunks name block ids. action restore: writes revision's content as a new revision.",
            properties =
                mapOf(
                    "document" to Schemas.string("Document id"),
                    "action" to Schemas.choice("What to do", listOf("list", "diff", "restore")),
                    "from" to Schemas.integer("diff: older revision"),
                    "to" to Schemas.integer("diff: newer revision, default current"),
                    "actor" to Schemas.choice("list: only revisions by this kind of actor", listOf("human", "agent")),
                    "since" to Schemas.string("list: only revisions at or after this ISO-8601 instant"),
                    "until" to Schemas.string("list: only revisions at or before this ISO-8601 instant"),
                    "revision" to Schemas.integer("restore: revision to bring back"),
                    "baseRevision" to Schemas.integer("restore: the revision you last read; a person's later change makes it a conflict"),
                    "opId" to Schemas.operationId,
                ),
            required = listOf("document", "action"),
            readOnly = false,
        )

    override fun call(
        arguments: Arguments,
        context: WriteContext,
    ): McpToolResult {
        val document = DocumentId(arguments.uuid("document"))
        return when (val action = arguments.string("action")) {
            "list" -> {
                list(arguments, context, document)
            }

            "diff" -> {
                diff(arguments, context, document)
            }

            "restore" -> {
                val revision = arguments.optionalInt("revision") ?: throw InvalidRequestException("restore needs revision")
                val base = arguments.optionalInt("baseRevision") ?: throw InvalidRequestException("restore needs baseRevision", READ_FIRST)
                WriteResults.of(documents.restore(context, document, revision, base), all = false)
            }

            else -> {
                throw InvalidRequestException("action must be list, diff, or restore, not $action")
            }
        }
    }

    private fun list(
        arguments: Arguments,
        context: WriteContext,
        document: DocumentId,
    ): McpToolResult {
        val filter =
            RevisionFilter(
                Wire.actorKind(arguments.optionalString("actor")),
                arguments.optionalInstant("since"),
                arguments.optionalInstant("until"),
            )
        val entries = queries.revisions(context.access, document, filter)
        val revisions =
            entries.map { entry ->
                val change = entry.revision.change
                mapOf(
                    "revision" to entry.revision.number,
                    "actor" to WorkspaceJson.actor(change.origin.actor),
                    "source" to
                        change.origin.source.name
                            .lowercase(),
                    "summary" to change.summary,
                    "changedBlockIds" to change.changedBlocks.map { it.value },
                    "at" to change.period.createdAt,
                    "threadIds" to entry.threads.map { it.value },
                )
            }
        return McpToolResult.data(mapOf("revisions" to revisions))
    }

    private fun diff(
        arguments: Arguments,
        context: WriteContext,
        document: DocumentId,
    ): McpToolResult {
        val from = arguments.optionalInt("from") ?: throw InvalidRequestException("diff needs from")
        val diff = queries.diff(context.access, document, from, arguments.optionalInt("to"))
        return McpToolResult.markdown(
            diff.unified,
            mapOf(
                "from" to diff.from,
                "to" to diff.to,
                "changedBlockIds" to diff.changedBlocks.map { it.value },
            ),
        )
    }
}

class DocsUploadTool(
    private val assets: AssetUseCase,
) : McpTool {
    override val definition =
        ToolDefinition(
            name = "docs_upload",
            title = "Upload an image",
            description =
                "Uploads a PNG, JPEG, GIF, WebP, or SVG (up to 20MB) as base64, or downloads one from a public URL, and returns the " +
                    "markdown " +
                    "that shows it. Always give alt text describing what the image shows.",
            properties =
                mapOf(
                    "space" to Schemas.string("Space slug"),
                    "fileName" to Schemas.string("File name"),
                    "contentBase64" to Schemas.string("File content as base64"),
                    "url" to Schemas.string("Public image URL, instead of contentBase64"),
                    "alt" to Schemas.string("Alt text for the returned markdown"),
                ),
            required = listOf("space"),
            readOnly = false,
        )

    override fun call(
        arguments: Arguments,
        context: WriteContext,
    ): McpToolResult {
        val space = SpaceSlug(arguments.string("space"))
        val url = arguments.optionalString("url")
        val asset =
            if (url != null) {
                assets.import(context, space, url)
            } else {
                val bytes = Base64.getMimeDecoder().decode(arguments.string("contentBase64"))
                assets.upload(context, space, UploadedFile(arguments.optionalString("fileName").orEmpty(), null, bytes))
            }
        val alt = arguments.optionalString("alt").orEmpty().replace("]", "\\]")
        return McpToolResult.data(
            mapOf(
                "assetId" to asset.id.value,
                "mediaType" to asset.content.mediaType.mime,
                "markdown" to "![$alt](asset:${asset.id})",
            ),
        )
    }
}
