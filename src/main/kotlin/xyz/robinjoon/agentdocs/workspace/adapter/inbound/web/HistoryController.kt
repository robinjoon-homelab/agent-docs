package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.RevisionEntry
import xyz.robinjoon.agentdocs.workspace.application.model.RevisionFilter
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentQueryUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase
import xyz.robinjoon.agentdocs.workspace.domain.ActorKind
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.history.BlockChange
import xyz.robinjoon.agentdocs.workspace.domain.document.history.RevisionDiff
import java.security.Principal
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/documents/{id}")
class HistoryController(
    private val queries: DocumentQueryUseCase,
    private val documents: DocumentUseCase,
) {
    @GetMapping("/revisions")
    fun revisions(
        @PathVariable id: UUID,
        query: RevisionListQuery,
        principal: Principal?,
    ): List<Map<String, Any?>> {
        val filter = RevisionFilter(Wire.actorKind(query.actor), query.from, query.to)
        return queries.revisions(CallerAccess.of(principal), DocumentId(id), filter).map(::entry)
    }

    @GetMapping("/revisions/{number}")
    fun revision(
        @PathVariable id: UUID,
        @PathVariable number: Int,
        principal: Principal?,
    ): Map<String, Any?> {
        val revision = queries.revision(CallerAccess.of(principal), DocumentId(id), number)
        return mapOf(
            "revision" to revision.number,
            "title" to revision.content.header.title,
            "path" to revision.content.header.path.value,
            "blocks" to revision.content.blocks.map(BlockJson::write),
            "actor" to WorkspaceJson.actor(revision.change.origin.actor),
            "createdAt" to revision.change.period.createdAt,
        )
    }

    // Without to, the diff runs to the current revision.
    @GetMapping("/diff")
    fun diff(
        @PathVariable id: UUID,
        @RequestParam from: Int,
        @RequestParam(required = false) to: Int?,
        principal: Principal?,
    ): Map<String, Any?> = diffOf(queries.diff(CallerAccess.of(principal), DocumentId(id), from, to))

    @PostMapping("/restore")
    fun restore(
        @PathVariable id: UUID,
        @RequestBody request: RestoreRequest,
        principal: Principal?,
    ): Map<String, Any?> {
        val written =
            documents.restore(
                CallerAccess.write(principal, request.opId, null),
                DocumentId(id),
                request.revision,
                request.baseRevision,
            )
        return mapOf("revision" to written.revision, "changedBlockIds" to written.changedBlocks.map { it.value })
    }

    private fun entry(entry: RevisionEntry): Map<String, Any?> {
        val change = entry.revision.change
        return mapOf(
            "revision" to entry.revision.number,
            "title" to entry.revision.title,
            "actor" to WorkspaceJson.actor(change.origin.actor),
            "source" to
                change.origin.source.name
                    .lowercase(),
            "summary" to change.summary,
            "changedBlockIds" to change.changedBlocks.map { it.value },
            "createdAt" to change.period.createdAt,
            "updatedAt" to change.period.updatedAt,
            "threadIds" to entry.threads.map { it.value },
        )
    }

    private fun diffOf(diff: RevisionDiff): Map<String, Any?> =
        mapOf(
            "from" to diff.from,
            "to" to diff.to,
            "changes" to diff.changes.map(::change),
            "unified" to diff.unified,
        )

    private fun change(change: BlockChange): Map<String, Any?> =
        mapOf(
            "blockId" to change.blockId.value,
            "change" to change.kind.name.lowercase(),
            "before" to change.before?.let(BlockJson::write),
            "after" to change.after?.let(BlockJson::write),
            "changedCells" to change.changedCells().map { mapOf("rowId" to it.rowId.value, "columnId" to it.columnId.value) },
        )
}

// Optional filters of the revision list, bound from query parameters. from and to are ISO-8601 instants.
data class RevisionListQuery(
    val actor: String?,
    val from: Instant?,
    val to: Instant?,
)

data class RestoreRequest(
    val revision: Int,
    val opId: String?,
    val baseRevision: Int? = null,
)
