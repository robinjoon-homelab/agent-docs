package xyz.robinjoon.agentdocs.webhook.application.service

import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.webhook.application.port.input.WebhookSweepUseCase
import xyz.robinjoon.agentdocs.webhook.domain.delivery.WebhookDelivery
import java.time.Clock
import java.time.Duration
import java.time.Instant

// Ids in the log are taken before commit, so reading after the last id seen would skip an entry that commits late
// under a smaller id. The short pass rereads a window of recent minutes, which catches the usual late commit right
// away; the full pass rereads everything since the oldest active endpoint started, so a later commit is never lost
// while the log keeps it. Pages commit one by one, so a full pass holds no long transaction.
@Transactional(readOnly = true)
class WebhookSweepService(
    private val planner: WebhookDeliveryPlanner,
    private val clock: Clock,
) : WebhookSweepUseCase {
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    override fun sweepRecent(): List<WebhookDelivery> = sweep(Instant.now(clock).minus(recent))

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    override fun reconcile(): List<WebhookDelivery> = sweep(null)

    private fun sweep(from: Instant?): List<WebhookDelivery> {
        val planned = ArrayList<WebhookDelivery>()
        var after: Long? = null
        do {
            val page = planner.planPage(from, after)
            planned.addAll(page.planned)
            after = page.next
        } while (after != null)
        return planned.toList()
    }

    private companion object {
        val recent: Duration = Duration.ofMinutes(10)
    }
}
