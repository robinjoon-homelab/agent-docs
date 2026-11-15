package xyz.robinjoon.agentdocs.workspace.adapter.inbound.scheduling

import org.springframework.scheduling.annotation.Scheduled
import xyz.robinjoon.agentdocs.workspace.application.port.input.EditSessionUseCase

class EditSessionExpiryScheduler(
    private val sessions: EditSessionUseCase,
) {
    @Scheduled(fixedDelay = 1_000)
    fun expire() {
        sessions.expire()
    }
}
