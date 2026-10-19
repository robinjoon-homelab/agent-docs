package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadChange
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadQuery
import xyz.robinjoon.agentdocs.workspace.application.port.input.ThreadUseCase
import xyz.robinjoon.agentdocs.workspace.domain.ActorKind
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId
import java.net.URI
import java.security.Principal
import java.time.Instant
import java.util.UUID

@RestController
class ThreadController(
    private val threads: ThreadUseCase,
) {
    @GetMapping("/api/documents/{id}/threads")
    fun forDocument(
        @PathVariable id: UUID,
        principal: Principal?,
    ): List<Map<String, Any?>> = threads.forDocument(CallerAccess.of(principal), DocumentId(id)).map(ThreadJson::thread)

    @PostMapping("/api/documents/{id}/threads")
    fun open(
        @PathVariable id: UUID,
        @RequestBody body: JsonNode,
        principal: Principal?,
    ): ResponseEntity<Map<String, Any?>> {
        val context = CallerAccess.write(principal, Wire.optional(body, "opId"), null)
        val view = threads.open(context, DocumentId(id), ThreadJson.anchorRequest(body.get("anchor")), Wire.required(body, "body"))
        return ResponseEntity.created(URI.create("/api/threads/${view.thread.id}")).body(ThreadJson.thread(view))
    }

    @PostMapping("/api/threads/{id}/comments")
    fun reply(
        @PathVariable id: UUID,
        @RequestBody body: JsonNode,
        principal: Principal?,
    ): ResponseEntity<Map<String, Any?>> {
        val context = CallerAccess.write(principal, Wire.optional(body, "opId"), null)
        val view = threads.reply(context, ThreadId(id), Wire.required(body, "body"), body.path("markAnswered").asBoolean(false))
        val comment = checkNotNull(view.written?.let(view.thread::comment)) { "a reply returns the comment it wrote" }
        return ResponseEntity.created(URI.create("/api/comments/${comment.id}")).body(ThreadJson.comment(comment))
    }

    // Moves the anchor, changes the status, links a revision, or any of these together as one change.
    @PatchMapping("/api/threads/{id}")
    fun change(
        @PathVariable id: UUID,
        @RequestBody body: JsonNode,
        principal: Principal?,
    ): Map<String, Any?> {
        val context = CallerAccess.write(principal, Wire.optional(body, "opId"), null)
        val change =
            ThreadChange(
                anchor = body.get("anchor")?.let(ThreadJson::anchorRequest),
                status = Wire.optional(body, "status")?.let(Wire::threadStatus),
                linkRevision = body.get("linkRevision")?.takeIf(JsonNode::isInt)?.intValue(),
            )
        return ThreadJson.thread(threads.change(context, ThreadId(id), change))
    }

    @PatchMapping("/api/comments/{id}")
    fun editComment(
        @PathVariable id: UUID,
        @RequestBody request: CommentRequest,
        principal: Principal?,
    ): Map<String, Any?> {
        val view = threads.editComment(CallerAccess.write(principal, request.opId, null), CommentId(id), request.body)
        return ThreadJson.comment(requireNotNull(view.thread.comment(CommentId(id))))
    }

    @DeleteMapping("/api/comments/{id}")
    fun deleteComment(
        @PathVariable id: UUID,
        @RequestParam(required = false) opId: String?,
        principal: Principal?,
    ): ResponseEntity<Unit> {
        threads.deleteComment(CallerAccess.write(principal, opId, null), CommentId(id))
        return ResponseEntity.noContent().build()
    }

    // The agent work queue: threads across visible spaces with the anchored block and its neighbors.
    @GetMapping("/api/threads")
    fun query(
        filter: ThreadFilter,
        principal: Principal?,
    ): List<Map<String, Any?>> = threads.query(CallerAccess.of(principal), filter.toQuery()).map(ThreadJson::thread)
}

data class CommentRequest(
    val body: String,
    val opId: String? = null,
)

// Query parameters of the thread query; every one is optional.
data class ThreadFilter(
    val space: UUID?,
    val document: UUID?,
    val status: List<String>?,
    val authorKind: String?,
    val mention: String?,
    val since: Instant?,
) {
    fun toQuery(): ThreadQuery =
        ThreadQuery(
            spaces = space?.let { setOf(SpaceId(it)) },
            document = document?.let(::DocumentId),
            statuses = status?.map(Wire::threadStatus)?.toSet(),
            authorKind = Wire.actorKind(authorKind, "authorKind"),
            mention = mention?.removePrefix("@"),
            since = since,
        )
}
