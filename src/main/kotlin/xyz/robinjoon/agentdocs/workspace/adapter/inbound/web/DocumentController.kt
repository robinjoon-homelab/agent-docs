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
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentWrite
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownRequest
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentQueryUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentProperties
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentStatus
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.MarkdownReader
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
) {
    @GetMapping("/api/spaces/{slug}/documents")
    fun list(
        @PathVariable slug: String,
        query: DocumentListQuery,
        principal: Principal?,
    ): List<Map<String, Any?>> {
        val filter = DocumentFilter(query.status?.let(Wire::status), query.tag, query.folder, false)
        return queries.list(CallerAccess.of(principal), SpaceSlug(slug), filter).map(WorkspaceJson::listing)
    }

    @GetMapping("/api/spaces/{slug}/trash")
    fun trashList(
        @PathVariable slug: String,
        principal: Principal?,
    ): List<Map<String, Any?>> =
        queries.list(CallerAccess.of(principal), SpaceSlug(slug), DocumentFilter(null, null, null, true)).map(WorkspaceJson::listing)

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
        val properties =
            DocumentProperties.of(
                Wire.strings(body.get("tags")),
                Wire.optional(body, "status")?.let(Wire::status) ?: DocumentStatus.DRAFT,
                Wire.optional(body, "assignee"),
            )
        val draft = DocumentDraft(DocumentPath(Wire.required(body, "path")), Wire.required(body, "title"), properties, blocksOf(body))
        val written =
            documents.create(
                CallerAccess.write(principal, Wire.optional(body, "opId"), Wire.optional(body, "summary")),
                SpaceSlug(slug),
                draft,
            )
        val created = WorkspaceJson.view(queries.open(CallerAccess.of(principal), written.document.id))
        return ResponseEntity.created(URI.create("/api/documents/${written.document.id}")).body(created)
    }

    @GetMapping("/api/documents/{id}")
    fun open(
        @PathVariable id: UUID,
        principal: Principal?,
    ): Map<String, Any?> = WorkspaceJson.view(queries.open(CallerAccess.of(principal), DocumentId(id)))

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
            )
        val chunk = queries.markdown(CallerAccess.of(principal), DocumentId(id), request)
        return mapOf(
            "documentId" to chunk.document.id.value,
            "revision" to chunk.document.revision,
            "markdown" to chunk.text,
            "nextCursor" to chunk.nextCursor,
            "totalBlocks" to chunk.totalBlocks,
        )
    }

    @PutMapping("/api/documents/{id}/content")
    fun save(
        @PathVariable id: UUID,
        @RequestBody body: JsonNode,
        principal: Principal?,
    ): Map<String, Any?> {
        val base =
            body.get("baseRevision")?.takeIf(JsonNode::isInt)?.intValue() ?: throw InvalidRequestException("baseRevision is required")
        val blocks = body.path("blocks").values().map(BlockJson::read)
        return writeResult(documents.save(CallerAccess.write(principal, Wire.optional(body, "opId"), null), DocumentId(id), base, blocks))
    }

    @PostMapping("/api/documents/{id}/edits")
    fun edit(
        @PathVariable id: UUID,
        @RequestBody body: JsonNode,
        principal: Principal?,
    ): Map<String, Any?> {
        val answering = Wire.optional(body, "answering")?.let { ThreadId(UUID.fromString(it)) }
        val context = CallerAccess.write(principal, Wire.optional(body, "opId"), Wire.optional(body, "summary"))
        return writeResult(documents.edit(context, DocumentId(id), EditJson.operations(body.get("operations")), answering))
    }

    // Answers with the whole document; its revision is the base for the next content save.
    @PatchMapping("/api/documents/{id}")
    fun changeHeader(
        @PathVariable id: UUID,
        @RequestBody body: JsonNode,
        principal: Principal?,
    ): Map<String, Any?> {
        documents.changeHeader(CallerAccess.write(principal, Wire.optional(body, "opId"), null), DocumentId(id), Wire.headerChange(body))
        return WorkspaceJson.view(queries.open(CallerAccess.of(principal), DocumentId(id)))
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

    private fun blocksOf(body: JsonNode) =
        body.get("markdown")?.takeIf(JsonNode::isString)?.let { markdown ->
            MarkdownReader.readBlocks(markdown.stringValue()).map { it.block }
        }
            ?: body.path("blocks").values().map(BlockJson::read)

    private fun writeResult(written: DocumentWrite): Map<String, Any?> =
        mapOf(
            "id" to written.document.id.value,
            "revision" to written.revision,
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
)

// Optional filters of the document list, bound from query parameters.
data class DocumentListQuery(
    val status: String?,
    val tag: String?,
    val folder: String?,
)
