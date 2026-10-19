package xyz.robinjoon.agentdocs.workspace.adapter.inbound.scheduling

import org.springframework.scheduling.annotation.Scheduled
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase

// The homelab runs only Deployments, so periodic work runs inside the application. Once a day, documents that have
// been in the trash for 30 days are removed.
class TrashCleanupScheduler(
    private val documents: DocumentUseCase,
) {
    @Scheduled(cron = "0 17 3 * * *", zone = "UTC")
    fun purgeExpiredTrash() {
        documents.purgeExpiredTrash()
    }
}
