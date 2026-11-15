package xyz.robinjoon.agentdocs.workspace.adapter.inbound.scheduling

import org.springframework.scheduling.annotation.Scheduled
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase

class PendingEditScheduler(
    private val documents: DocumentUseCase,
) {
    @Scheduled(fixedDelay = 1_000)
    fun finalizeExpired() {
        documents.finalizeExpiredEdits()
    }
}
