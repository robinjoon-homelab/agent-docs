package xyz.robinjoon.agentdocs.workspace.adapter.inbound.scheduling

import org.springframework.scheduling.annotation.Scheduled
import xyz.robinjoon.agentdocs.workspace.application.port.input.UploadGrantUseCase

// A grant is useless once it expires, so nothing keeps it after that.
class UploadGrantCleanupScheduler(
    private val uploads: UploadGrantUseCase,
) {
    @Scheduled(cron = "0 23 3 * * *", zone = "UTC")
    fun forgetExpiredGrants() {
        uploads.forgetExpired()
    }
}
