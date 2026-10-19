package xyz.robinjoon.agentdocs.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import xyz.robinjoon.agentdocs.application.model.ActivityActor
import xyz.robinjoon.agentdocs.application.model.ActivityEntry
import xyz.robinjoon.agentdocs.application.model.ActivityQuery
import xyz.robinjoon.agentdocs.application.model.ActivityRecord
import xyz.robinjoon.agentdocs.application.model.ActivitySubject
import xyz.robinjoon.agentdocs.application.port.output.ActivityCatalog
import xyz.robinjoon.agentdocs.application.port.output.ActivityReporter
import java.time.Instant
import java.time.ZoneOffset

class ExposedActivityLog :
    ActivityReporter,
    ActivityCatalog {
    override fun record(record: ActivityRecord) {
        ActivitiesTable.insert {
            it[kind] = record.kind
            it[actorKind] = record.actor.kind
            it[actorUserId] = record.actor.userId
            it[actorLogin] = record.actor.login
            it[actorConnectionId] = record.actor.connectionId
            it[actorClientName] = record.actor.clientName
            it[spaceId] = record.subject.spaceId
            it[documentId] = record.subject.documentId
            it[threadId] = record.subject.threadId
            it[revision] = record.subject.revision
            it[summary] = record.summary
            it[occurredAt] = record.at.atOffset(ZoneOffset.UTC)
        }
    }

    override fun pruneBefore(cutoff: Instant): Int = ActivitiesTable.deleteWhere { occurredAt less cutoff.atOffset(ZoneOffset.UTC) }

    override fun find(query: ActivityQuery): List<ActivityEntry> {
        if (query.spaceIds?.isEmpty() == true) return emptyList()
        val select = ActivitiesTable.selectAll()
        query.spaceIds?.let { spaces -> select.andWhere { ActivitiesTable.spaceId inList spaces } }
        query.since?.let { since -> select.andWhere { ActivitiesTable.occurredAt greater since.atOffset(ZoneOffset.UTC) } }
        query.beforeId?.let { before -> select.andWhere { ActivitiesTable.id less before } }
        val order = if (query.oldestFirst) SortOrder.ASC else SortOrder.DESC
        return select.orderBy(ActivitiesTable.id to order).limit(query.limit).map { it.toEntry() }
    }

    private fun ResultRow.toEntry(): ActivityEntry {
        val actor =
            ActivityActor(
                this[ActivitiesTable.actorKind],
                this[ActivitiesTable.actorUserId],
                this[ActivitiesTable.actorLogin],
                this[ActivitiesTable.actorConnectionId],
                this[ActivitiesTable.actorClientName],
            )
        val subject =
            ActivitySubject(
                this[ActivitiesTable.spaceId],
                this[ActivitiesTable.documentId],
                this[ActivitiesTable.threadId],
                this[ActivitiesTable.revision],
            )
        val record =
            ActivityRecord(
                this[ActivitiesTable.kind],
                actor,
                subject,
                this[ActivitiesTable.summary],
                this[ActivitiesTable.occurredAt].toInstant(),
            )
        return ActivityEntry(this[ActivitiesTable.id], record)
    }
}
