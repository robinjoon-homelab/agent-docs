package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentEventsUseCase
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import java.util.UUID
import java.util.concurrent.ScheduledExecutorService

@RestController
class DocumentEventsController(
    private val snapshots: DocumentEventsUseCase,
    private val callers: DocumentEventCaller,
    private val scheduler: ScheduledExecutorService,
) {
    @GetMapping("/api/documents/{id}/events", produces = ["text/event-stream"])
    fun watch(
        @PathVariable id: UUID,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): SseEmitter {
        val credential = callers.credential(request)
        val document = DocumentId(id)
        snapshots.snapshot(callers.current(credential), document)
        response.setHeader("Cache-Control", "no-store")
        response.setHeader("X-Accel-Buffering", "no")
        val emitter = SseEmitter(300_000L)
        DocumentEventStream(emitter, snapshots, callers, credential, document).start(scheduler)
        return emitter
    }
}
