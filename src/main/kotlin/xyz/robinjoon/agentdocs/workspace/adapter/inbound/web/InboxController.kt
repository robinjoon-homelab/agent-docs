package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import xyz.robinjoon.agentdocs.workspace.application.port.input.InboxUseCase
import xyz.robinjoon.agentdocs.workspace.domain.inbox.InboxItemId
import java.security.Principal
import java.util.UUID

@RestController
@RequestMapping("/api/inbox")
class InboxController(
    private val inbox: InboxUseCase,
) {
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "false") unread: Boolean,
        principal: Principal?,
    ): List<Map<String, Any?>> =
        inbox.list(CallerAccess.of(principal), unread).map { view ->
            val notice = view.item.notice
            mapOf(
                "id" to view.item.id.value,
                "kind" to notice.kind.name.lowercase(),
                "space" to WorkspaceJson.spaceRef(view.space),
                "documentId" to notice.target.documentId.value,
                "documentTitle" to view.documentTitle,
                "threadId" to notice.target.threadId.value,
                "commentId" to notice.target.commentId.value,
                "actor" to WorkspaceJson.actor(notice.actor),
                "excerpt" to notice.excerpt,
                "createdAt" to view.item.delivery.deliveredAt,
                "readAt" to view.item.delivery.readAt,
            )
        }

    @GetMapping("/count")
    fun count(principal: Principal?): Map<String, Int> = mapOf("unread" to inbox.unreadCount(CallerAccess.of(principal)))

    @PostMapping("/{id}/read")
    fun markRead(
        @PathVariable id: UUID,
        principal: Principal?,
    ): ResponseEntity<Unit> {
        inbox.markRead(CallerAccess.of(principal), InboxItemId(id))
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/read-all")
    fun markAllRead(principal: Principal?): ResponseEntity<Unit> {
        inbox.markAllRead(CallerAccess.of(principal))
        return ResponseEntity.noContent().build()
    }
}
