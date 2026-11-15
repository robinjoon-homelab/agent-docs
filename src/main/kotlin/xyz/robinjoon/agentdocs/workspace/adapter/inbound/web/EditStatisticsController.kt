package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import xyz.robinjoon.agentdocs.workspace.application.port.input.EditSessionUseCase
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import java.security.Principal
import java.time.Instant
import java.util.UUID

@RestController
class EditStatisticsController(
    private val sessions: EditSessionUseCase,
) {
    @GetMapping("/api/documents/{id}/edit-statistics")
    fun statistics(
        @PathVariable id: UUID,
        principal: Principal?,
    ): Map<String, Any> = EditStatisticsJson.statistics(sessions.statistics(CallerAccess.of(principal), DocumentId(id)))

    @PostMapping("/api/documents/{id}/edit-statistics/close")
    fun close(
        @PathVariable id: UUID,
        @RequestParam tabId: UUID,
        @RequestParam lastInputAt: Instant,
        principal: Principal?,
    ) {
        sessions.closeHuman(CallerAccess.of(principal), DocumentId(id), tabId, lastInputAt)
    }

    @PostMapping("/api/documents/{id}/edit-statistics/start")
    fun start(
        @PathVariable id: UUID,
        @RequestBody body: EditOperationInput,
        principal: Principal?,
    ): Map<String, Any?> = EditStatisticsJson.session(sessions.start(CallerAccess.write(principal, body.opId, null), DocumentId(id)))

    @PostMapping("/api/edit-sessions/{id}/end")
    fun end(
        @PathVariable id: UUID,
        @RequestBody body: EditOperationInput,
        principal: Principal?,
    ): Map<String, Any?> = EditStatisticsJson.session(sessions.end(CallerAccess.write(principal, body.opId, null), id))
}

data class EditOperationInput(
    val opId: String,
)
