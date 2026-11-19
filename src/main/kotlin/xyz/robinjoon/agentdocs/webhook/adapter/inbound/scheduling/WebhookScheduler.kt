package xyz.robinjoon.agentdocs.webhook.adapter.inbound.scheduling

import org.springframework.scheduling.annotation.Scheduled
import xyz.robinjoon.agentdocs.webhook.application.port.input.WebhookDispatchUseCase
import xyz.robinjoon.agentdocs.webhook.application.port.input.WebhookSweepUseCase

// Without an active endpoint both jobs find nothing and send nothing.
class WebhookScheduler(
    private val sweep: WebhookSweepUseCase,
    private val dispatch: WebhookDispatchUseCase,
) {
    @Scheduled(fixedRateString = "PT5S", initialDelayString = "PT30S")
    fun sweepRecent() {
        sweep.sweepRecent()
    }

    @Scheduled(fixedDelayString = "PT5S", initialDelayString = "PT30S", scheduler = "webhookDispatchScheduler")
    fun deliver() {
        dispatch.dispatchDue()
    }

    // Every hour, and soon after start: plan anything the short window missed, back to the oldest active endpoint's
    // start, including events logged while the app was down.
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1M")
    fun reconcile() {
        sweep.reconcile()
    }
}
