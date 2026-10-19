package xyz.robinjoon.agentdocs.adapter.inbound.scheduling

import org.springframework.scheduling.annotation.Scheduled
import xyz.robinjoon.agentdocs.application.port.input.ActivityRetentionUseCase

// Once a day, activity older than a year is dropped.
class ActivityRetentionScheduler(
    private val retention: ActivityRetentionUseCase,
) {
    @Scheduled(cron = "0 37 3 * * *", zone = "UTC")
    fun prune() {
        retention.prune()
    }
}
