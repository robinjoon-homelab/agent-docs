package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentDraft
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentFilter
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentListPosition
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentListingPage
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentView
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentWrite
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownRequest
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentQueryUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.SpaceUseCase
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentProperties
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentStatus
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.MarkdownReader
import xyz.robinjoon.agentdocs.workspace.domain.space.Space
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId
import java.net.URI
import java.security.Principal
import java.util.UUID

// Writes take an optional opId so a retried request applies once, in the body or, for a write without one, as a
// query parameter, and an optional one-line summary for history.
@RestController
class DocumentController(
    private val documents: DocumentUseCase,
    private val queries: DocumentQueryUseCase,
    private val spaces: SpaceUseCase,
    private val recorder: EditStatisticsRecorder,
) {
    @GetMapping("/api/spaces/{slug}/documents")
    fun list(
        @PathVariable slug: String,
        query: DocumentListQuery,
        principal: Principal?,
    ): ResponseEntity<List<Map<String, Any?>>> {
        val filter =
            DocumentFilter(query.status?.let(Wire::status), query.tag, query.folder, false, query.cursor?.let(DocumentListPosition::read))
        return listing(queries.list(CallerAccess.of(principal), SpaceSlug(slug), filter))
    }

    @GetMapping("/api/spaces/{slug}/trash")
    fun trashList(
        @PathVariable slug: String,
        @RequestParam(required = false) cursor: String?,
        principal: Principal?,
    ): ResponseEntity<List<Map<String, Any?>>> {
        val filter = DocumentFilter(null, null, null, true, cursor?.let(DocumentListPosition::read))
        return listing(queries.list(CallerAccess.of(principal), SpaceSlug(slug), filter))
    }

    private fun listing(page: DocumentListingPage): ResponseEntity<List<Map<String, Any?>>> {
        val response = ResponseEntity.ok()
        page.nextCursor?.let { response.header("X-Next-Cursor", it) }
        return response.body(page.documents.map(WorkspaceJson::listing))
    }

    @GetMapping("/api/documents/recent")
    fun recent(
        @RequestParam(defaultValue = "20") limit: Int,
        principal: Principal?,
    ): List<Map<String, Any?>> = queries.recent(CallerAccess.of(principal), limit).map(WorkspaceJson::listing)

    // Content comes as blocks from the editor or as markdown from an agent. Answers with the whole new document.
    @PostMapping("/api/spaces/{slug}/documents")
    fun create(
        @PathVariable slug: String,
        @RequestBody body: JsonNode,
        principal: Principal?,
    ): ResponseEntity<Map<String, Any?>> {
        Wire.requireObject(body)
        val properties =
            DocumentProperties.of(
                Wire.strings(body.get("tags")),
                Wire.optional(body, "status")?.let(Wire::status) ?: DocumentStatus.DRAFT,
                Wire.optional(body, "assignee"),
            )
        val draft =
            DocumentDraft(
                DocumentPath(Wire.required(body, "path")),
                Wire.required(body, "title"),
                properties,
                creationBlocks(body),
                Wire.optional(body, "type") ?: "doc",
            )
        val written =
            documents.create(
                RevisionMetadataJson.context(
                    CallerAccess.write(principal, Wire.optional(body, "opId"), Wire.optional(body, "summary")),
                    body,
                ),
                SpaceSlug(slug),
                draft,
            )
        record(principal, written, body)
        val access = CallerAccess.of(principal)
        val created = responseView(written, queries.open(access, written.document.id).space, access)
        return ResponseEntity.created(URI.create("/api/documents/${written.document.id}")).body(created)
    }

    private fun creationBlocks(body: JsonNode): List<Block> {
        if ((Wire.optional(body, "type") ?: "doc") != "flow") return blocksOf(body)
        val frame = Wire.optional(body, "frame") ?: "phone"
        val prefix = Wire.optional(body, "idPrefix") ?: "M"
        val markdown = Wire.optional(body, "markdown")
        if (markdown != null) {
            return xyz.robinjoon.agentdocs.workspace.application.model.FlowDraftContent.markdown(
                MarkdownReader.readBlocks(markdown, "flow"),
                frame,
                prefix,
            )
        }
        return body.get("blocks")?.let(::readBlocks) ?: xyz.robinjoon.agentdocs.workspace.domain.document.FlowRules
            .initial(frame, prefix)
    }

    @GetMapping("/api/documents/{id}")
    fun open(
        @PathVariable id: UUID,
        principal: Principal?,
    ): Map<String, Any?> {
        val access = CallerAccess.of(principal)
        val view = queries.open(access, DocumentId(id))
        return WorkspaceJson.view(view) + WorkspaceJson.permission(access, view.document.spaceId)
    }

    // The markdown docs_read gives agents, for scripts and agents that use REST.
    @GetMapping("/api/documents/{id}/markdown")
    fun markdown(
        @PathVariable id: UUID,
        query: MarkdownQuery,
        principal: Principal?,
    ): Map<String, Any?> {
        val request =
            MarkdownRequest(
                Wire.markdownFormat(query.format),
                query.section?.let(::BlockId),
                query.blocks?.map(::BlockId),
                query.cursor ?: 0,
                query.revision,
            )
        val chunk = queries.markdown(CallerAccess.of(principal), DocumentId(id), request)
        return mapOf(
            "documentId" to chunk.document.id.value,
            "revision" to chunk.document.revision,
            "version" to chunk.document.version,
            "markdown" to chunk.text,
            "nextCursor" to chunk.nextCursor,
            "contiguous" to true,
            "totalBlocks" to chunk.totalBlocks,
            "agentGuide" to
                spaces
                    .agentGuide(CallerAccess.of(principal), chunk.document.spaceId)
                    ?.takeIf { it.documentId != chunk.document.id }
                    ?.let(SpaceJson::guideOf),
        )
    }

    @PutMapping("/api/documents/{id}/content")
    fun save(
        @PathVariable id: UUID,
        @RequestBody body: JsonNode,
        principal: Principal?,
    ): Map<String, Any?> {
        Wire.requireObject(body)
        val base =
            body.get("baseVersion")?.takeIf(JsonNode::isInt)?.intValue() ?: throw InvalidRequestException("baseVersion is required")
        val blocks = readBlocks(body.get("blocks"))
        val context = RevisionMetadataJson.context(CallerAccess.write(principal, Wire.optional(body, "opId"), null), body)
        val written =
            recorder.countConflict(context.access, DocumentId(id)) {
                documents.save(
                    context,
                    DocumentId(id),
                    base,
                    blocks,
                )
            }
        record(principal, written, body)
        return writeResult(written)
    }

    @PostMapping("/api/documents/{id}/finalize")
    fun finalizeEdits(
        @PathVariable id: UUID,
        @RequestParam baseVersion: Int,
        @RequestParam(required = false) opId: String?,
        principal: Principal?,
    ): Map<String, Any?> = writeResult(documents.finalizeEdits(CallerAccess.write(principal, opId, null), DocumentId(id), baseVersion))

    @PostMapping("/api/documents/{id}/edits")
    fun edit(
        @PathVariable id: UUID,
        @RequestBody body: JsonNode,
        principal: Principal?,
    ): Map<String, Any?> {
        Wire.requireObject(body)
        val answering = Wire.optional(body, "answering")?.let { ThreadId(Wire.identifier(it, "answering")) }
        val context =
            RevisionMetadataJson.context(
                CallerAccess.write(principal, Wire.optional(body, "opId"), Wire.optional(body, "summary")),
                body,
            )
        val written =
            recorder.countConflict(context.access, DocumentId(id)) {
                documents.edit(
                    context,
                    DocumentId(id),
                    EditJson.operations(
                        body.get("operations"),
                        queries
                            .open(context.access, DocumentId(id))
                            .document.header.type,
                    ),
                    answering,
                )
            }
        record(principal, written, body)
        return writeResult(written)
    }

    // Answers with the whole document; its revision is the base for the next content save.
    @PatchMapping("/api/documents/{id}")
    fun changeHeader(
        @PathVariable id: UUID,
        @RequestBody body: JsonNode,
        principal: Principal?,
    ): Map<String, Any?> {
        Wire.requireObject(body)
        val context = RevisionMetadataJson.context(CallerAccess.write(principal, Wire.optional(body, "opId"), null), body)
        val written =
            recorder.countConflict(context.access, DocumentId(id)) {
                documents.changeHeader(
                    context,
                    DocumentId(id),
                    Wire.headerChange(body),
                )
            }
        record(principal, written, body)
        val access = CallerAccess.of(principal)
        return responseView(written, queries.open(access, DocumentId(id)).space, access)
    }

    @PostMapping("/api/documents/{id}/trash")
    fun trash(
        @PathVariable id: UUID,
        @RequestParam(required = false) opId: String?,
        principal: Principal?,
    ): ResponseEntity<Unit> {
        documents.trash(CallerAccess.write(principal, opId, null), DocumentId(id))
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/api/documents/{id}/untrash")
    fun untrash(
        @PathVariable id: UUID,
        @RequestParam(required = false) opId: String?,
        principal: Principal?,
    ): ResponseEntity<Unit> {
        documents.untrash(CallerAccess.write(principal, opId, null), DocumentId(id))
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/api/documents/{id}")
    fun purge(
        @PathVariable id: UUID,
        @RequestParam(required = false) opId: String?,
        principal: Principal?,
    ): ResponseEntity<Unit> {
        documents.purge(CallerAccess.write(principal, opId, null), DocumentId(id))
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build()
    }

    private fun record(
        principal: Principal?,
        written: DocumentWrite,
        body: JsonNode,
    ) {
        val context = CallerAccess.write(principal, null, null)
        recorder.human(context, written, body)
        if (context.access.actor.isAgent) recorder.agent(context.access, written.document.id)
    }

    // space is where the document is now. A retried write answers with its first result, whose document may still
    // name the space it was in before a move, so the permission comes from the current space, never the stored write.
    private fun responseView(
        written: DocumentWrite,
        space: Space,
        access: Access,
    ): Map<String, Any?> {
        val view =
            WorkspaceJson.view(DocumentView(written.document, space, written.marks)) +
                WorkspaceJson.permission(access, space.id) +
                mapOf("alsoChanged" to written.related.map(WorkspaceJson::related))
        val captured = written.space ?: return view
        return view + mapOf("space" to mapOf("id" to captured.id.value, "slug" to captured.slug.value, "name" to captured.name))
    }

    private fun blocksOf(body: JsonNode) =
        Wire.optional(body, "markdown")?.let { markdown -> MarkdownReader.readBlocks(markdown).map { it.block } }
            ?: body.get("blocks")?.let(::readBlocks).orEmpty()

    private fun readBlocks(node: JsonNode?): List<Block> {
        if (node == null || !node.isArray) throw InvalidRequestException("blocks must be an array")
        return node.values().map(BlockJson::read)
    }

    private fun writeResult(written: DocumentWrite): Map<String, Any?> =
        mapOf(
            "id" to written.document.id.value,
            "type" to written.document.header.type,
            "revision" to written.revision,
            "version" to written.document.version,
            "amended" to written.amended,
            "unchanged" to written.unchanged,
            "merged" to written.merged,
            "changedBlockIds" to written.changedBlocks.map { it.value },
            "blocks" to written.document.blocks.map(BlockJson::write),
        )
}

// What part of a document to read as markdown, bound from query parameters.
data class MarkdownQuery(
    val format: String?,
    val section: String?,
    val blocks: List<String>?,
    val cursor: Int?,
    val revision: Int?,
)

// Optional filters of the document list, bound from query parameters.
data class DocumentListQuery(
    val status: String?,
    val tag: String?,
    val folder: String?,
    val cursor: String?,
)
