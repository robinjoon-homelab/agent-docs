package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

import xyz.robinjoon.agentdocs.access.application.model.UploadToken
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.EditStatisticsRecorder
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.ImageUploads
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.McpToolResult
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.RevisionMetadataJson
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.Wire
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WorkspaceJson
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.RevisionFilter
import xyz.robinjoon.agentdocs.workspace.application.model.UploadPass
import xyz.robinjoon.agentdocs.workspace.application.model.UploadedFile
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.input.AssetUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentAssetUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentQueryUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.UploadGrantUseCase
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetMediaType
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import java.util.Base64
import java.util.UUID

class DocsHistoryTool(
    private val queries: DocumentQueryUseCase,
    private val documents: DocumentUseCase,
    private val recorder: EditStatisticsRecorder,
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
                    "baseVersion" to Schemas.integer("restore: the content version you last read; any later change is a conflict"),
                    "codeReferences" to Schemas.objects("Related repository, full commit SHA or positive pullRequest, at most 20"),
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
                val base = arguments.optionalInt("baseVersion") ?: throw InvalidRequestException("restore needs baseVersion", READ_FIRST)
                val written = documents.restore(RevisionMetadataJson.context(context, arguments.node), document, revision, base)
                recorder.agent(context.access, written.document.id)
                WriteResults.of(written, all = false)
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
                    "name" to entry.revision.name,
                    "actor" to WorkspaceJson.actor(change.origin.actor),
                    "source" to
                        change.origin.source.name
                            .lowercase(),
                    "summary" to change.summary,
                    "changedBlockIds" to change.changedBlocks.map { it.value },
                    "at" to change.period.createdAt,
                    "threadIds" to entry.threads.map { it.value },
                ) + RevisionMetadataJson.fields(change.origin)
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
        // The header names both revisions and every hunk names its block, so the text alone is the whole result.
        return McpToolResult.markdown(diff.unified)
    }
}

class DocsUploadTool(
    private val assets: AssetUseCase,
    private val uploads: UploadGrantUseCase,
    private val uploadEndpoint: String,
    private val documentAssets: DocumentAssetUseCase,
) : McpTool {
    override val definition =
        ToolDefinition(
            name = "docs_upload",
            title = "Upload an attachment or read HTML source",
            description =
                "Uploads images, PDF, UTF-8 HTML/text, or binary downloads (up to 20MB). Pass assetId to read an HTML original. " +
                    "HTML markdown uses the mockup caption; replace that block reference to publish a new revision. Always give alt text " +
                    "describing what the image shows. Choose by where the image is. A local file, if you can run shell commands that " +
                    "reach this server: leave out contentBase64 and url, then run the curl command you get back with the file's path; " +
                    "any size works and nothing is copied by hand. A public image: pass url. No shell, or the command is blocked: pass " +
                    "contentBase64, but only up to about 5KB, because long base64 written out in a tool call gets mistyped and a " +
                    "damaged image may still be stored. For a bigger file without a shell, ask a person to paste it in the web editor.",
            properties =
                mapOf(
                    "space" to Schemas.string("Space slug"),
                    "document" to Schemas.string("Document ID for resolving an HTML asset reference after transfer; use with assetId"),
                    "assetId" to Schemas.string("Read the UTF-8 HTML source of this asset instead of uploading"),
                    "mediaType" to Schemas.string("Declared content type, with contentBase64"),
                    "fileName" to Schemas.string("File name, with contentBase64. An upload command sends the file's own name"),
                    "contentBase64" to
                        Schemas.string(
                            "File content as base64, up to about 5KB and only without a shell; leave it out to get an upload command",
                        ),
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
        arguments.optionalUuid("assetId")?.let { return sourceOf(context, it, arguments.optionalUuid("document")) }
        val space = SpaceSlug(arguments.string("space"))
        val url = arguments.optionalString("url")
        val content = arguments.optionalString("contentBase64")
        val alt = arguments.optionalString("alt").orEmpty()
        if (url == null && content == null) return commandFor(uploads.grant(context, space, alt))
        val asset =
            if (url != null) {
                assets.import(context, space, url)
            } else {
                assets.upload(
                    context,
                    space,
                    UploadedFile(
                        arguments.optionalString("fileName").orEmpty(),
                        arguments.optionalString("mediaType"),
                        decoded(content.orEmpty()),
                    ),
                )
            }
        return McpToolResult.data(ImageUploads.resultOf(asset, alt))
    }

    private fun sourceOf(
        context: WriteContext,
        id: UUID,
        document: UUID?,
    ): McpToolResult {
        val file =
            if (document == null) {
                assets.content(context.access, AssetId(id))
            } else {
                documentAssets.content(context.access, DocumentId(document), AssetId(id))
            }
        if (file.asset.content.mediaType != AssetMediaType.HTML) {
            throw InvalidRequestException("only HTML assets have preview source")
        }
        return McpToolResult.data(mapOf("assetId" to id, "source" to file.bytes.toString(Charsets.UTF_8)))
    }

    private fun commandFor(pass: UploadPass): McpToolResult {
        val token = UploadToken.of(pass.connectionId, pass.secret)
        return McpToolResult.data(
            mapOf(
                "command" to UploadCommand.of(token, uploadEndpoint),
                "expiresAt" to pass.expiresAt.toString(),
                "next" to
                    "Put the file's absolute path in place of ${UploadCommand.PATH_SLOT}, on its own line exactly as it is, with " +
                    "no quotes or escaping, and run all the lines in your shell. It prints assetId, mediaType, and markdown. It " +
                    "works for one file until expiresAt; after that, call docs_upload again.",
            ),
        )
    }

    private fun decoded(content: String): ByteArray =
        try {
            Base64.getMimeDecoder().decode(content)
        } catch (exception: IllegalArgumentException) {
            throw InvalidRequestException(
                "contentBase64 is not valid base64",
                "it was probably mistyped; leave out contentBase64 and url to get an upload command you can run in a shell",
                exception,
            )
        }
}
