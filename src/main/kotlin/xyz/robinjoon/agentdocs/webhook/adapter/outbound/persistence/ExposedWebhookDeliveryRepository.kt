package xyz.robinjoon.agentdocs.webhook.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.between
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import xyz.robinjoon.agentdocs.webhook.application.port.output.WebhookDeliveryRepository
import xyz.robinjoon.agentdocs.webhook.domain.delivery.DeliveryAttempt
import xyz.robinjoon.agentdocs.webhook.domain.delivery.DeliveryKey
import xyz.robinjoon.agentdocs.webhook.domain.delivery.DeliveryProgress
import xyz.robinjoon.agentdocs.webhook.domain.delivery.DeliveryState
import xyz.robinjoon.agentdocs.webhook.domain.delivery.StoredEvent
import xyz.robinjoon.agentdocs.webhook.domain.delivery.WebhookDelivery
import xyz.robinjoon.agentdocs.webhook.domain.delivery.WebhookDeliveryId
import xyz.robinjoon.agentdocs.webhook.domain.delivery.WebhookEvent
import xyz.robinjoon.agentdocs.webhook.domain.endpoint.WebhookEndpointId
import java.time.Instant
import java.time.ZoneOffset

class ExposedWebhookDeliveryRepository : WebhookDeliveryRepository {
    override fun addAll(deliveries: List<WebhookDelivery>) {
        WebhookDeliveriesTable.batchInsert(deliveries, shouldReturnGeneratedValues = false) { delivery ->
            this[WebhookDeliveriesTable.id] = delivery.id.value
            this[WebhookDeliveriesTable.endpointId] = delivery.endpointId.value
            this[WebhookDeliveriesTable.eventType] = delivery.event.type
            this[WebhookDeliveriesTable.sourceActivityId] = delivery.event.source
            this[WebhookDeliveriesTable.mentionedLogin] = delivery.event.mentioned.orEmpty()
            this[WebhookDeliveriesTable.occurredAt] = delivery.event.occurredAt.atOffset(ZoneOffset.UTC)
            this[WebhookDeliveriesTable.payload] = delivery.event.payload
            writeProgress(this, delivery.progress)
        }
    }

    override fun keysAmong(
        endpoints: Set<WebhookEndpointId>,
        sources: LongRange,
    ): Set<DeliveryKey> {
        if (endpoints.isEmpty() || sources.isEmpty()) return emptySet()
        val table = WebhookDeliveriesTable
        return table
            .select(table.endpointId, table.sourceActivityId, table.eventType, table.mentionedLogin)
            .where { (table.endpointId inList endpoints.map { it.value }) and table.sourceActivityId.between(sources.first, sources.last) }
            .map {
                val mentioned = it[table.mentionedLogin].ifEmpty { null }
                DeliveryKey(WebhookEndpointId(it[table.endpointId]), it[table.sourceActivityId], it[table.eventType], mentioned)
            }.toSet()
    }

    // The endpoint check is a subquery rather than a join, so only the delivery row is locked and planning, which
    // locks endpoints, does not wait on sending.
    override fun lockNextDue(at: Instant): WebhookDelivery? {
        val table = WebhookDeliveriesTable
        val active = WebhookEndpointsTable.select(WebhookEndpointsTable.id).where { WebhookEndpointsTable.stoppedAt.isNull() }
        return table
            .selectAll()
            .where { (table.state eq DeliveryState.PENDING) and (table.nextAttemptAt lessEq at.atOffset(ZoneOffset.UTC)) }
            .andWhere { table.endpointId inSubQuery active }
            .orderBy(table.nextAttemptAt to SortOrder.ASC, table.id to SortOrder.ASC)
            .limit(1)
            .forUpdate()
            .singleOrNull()
            ?.toDelivery()
    }

    override fun findByIdForUpdate(id: WebhookDeliveryId): WebhookDelivery? =
        WebhookDeliveriesTable
            .selectAll()
            .where { WebhookDeliveriesTable.id eq id.value }
            .forUpdate()
            .singleOrNull()
            ?.toDelivery()

    // Only the progress changes; the event and its body stay as planned.
    override fun update(delivery: WebhookDelivery) {
        val updated =
            WebhookDeliveriesTable.update({ WebhookDeliveriesTable.id eq delivery.id.value }) { writeProgress(it, delivery.progress) }
        check(updated == 1) { "Webhook delivery to update does not exist: ${delivery.id}" }
    }

    override fun recent(
        endpoint: WebhookEndpointId,
        limit: Int,
    ): List<WebhookDelivery> {
        val table = WebhookDeliveriesTable
        return table
            .selectAll()
            .where { table.endpointId eq endpoint.value }
            .orderBy(table.sourceActivityId to SortOrder.DESC, table.eventType to SortOrder.ASC, table.mentionedLogin to SortOrder.ASC)
            .limit(limit)
            .map { it.toDelivery() }
    }

    private fun writeProgress(
        row: UpdateBuilder<*>,
        progress: DeliveryProgress,
    ) {
        row[WebhookDeliveriesTable.state] = progress.state
        row[WebhookDeliveriesTable.attempts] = progress.attempts
        row[WebhookDeliveriesTable.nextAttemptAt] = progress.nextAttemptAt?.atOffset(ZoneOffset.UTC)
        row[WebhookDeliveriesTable.lastAttemptAt] = progress.last?.at?.atOffset(ZoneOffset.UTC)
        row[WebhookDeliveriesTable.lastStatus] = progress.last?.status
        row[WebhookDeliveriesTable.lastError] = progress.last?.error
    }

    private fun ResultRow.toDelivery(): WebhookDelivery {
        val table = WebhookDeliveriesTable
        val stored = StoredEvent(this[table.sourceActivityId], this[table.occurredAt].toInstant(), this[table.payload])
        val event = WebhookEvent.restore(this[table.eventType], stored, this[table.mentionedLogin].ifEmpty { null })
        val last = this[table.lastAttemptAt]?.let { DeliveryAttempt(it.toInstant(), this[table.lastStatus], this[table.lastError]) }
        val progress = DeliveryProgress(this[table.state], this[table.attempts], this[table.nextAttemptAt]?.toInstant(), last)
        return WebhookDelivery.restore(WebhookDeliveryId(this[table.id]), WebhookEndpointId(this[table.endpointId]), event, progress)
    }
}
