package xyz.robinjoon.agentdocs.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import xyz.robinjoon.agentdocs.application.model.ActivityActor
import xyz.robinjoon.agentdocs.application.model.ActivityEntry
import xyz.robinjoon.agentdocs.application.model.ActivityQuery
import xyz.robinjoon.agentdocs.application.model.ActivityRecord
import xyz.robinjoon.agentdocs.application.model.ActivityScan
import xyz.robinjoon.agentdocs.application.model.ActivitySubject
import xyz.robinjoon.agentdocs.application.model.CreatedComment
import xyz.robinjoon.agentdocs.application.port.output.ActivityCatalog
import xyz.robinjoon.agentdocs.application.port.output.ActivityReporter
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class ExposedActivityLog :
    ActivityReporter,
    ActivityCatalog {
    override fun record(record: ActivityRecord) {
        val id =
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
                it[createdCommentId] = record.createdComment?.id
            }[ActivitiesTable.id]
        val mentions = record.createdComment?.mentions.orEmpty()
        if (mentions.isEmpty()) return
        ActivityMentionsTable.batchInsert(mentions, shouldReturnGeneratedValues = false) { login ->
            this[ActivityMentionsTable.activityId] = id
            this[ActivityMentionsTable.login] = login
        }
    }

    override fun redactCommentExcerpt(commentId: UUID) {
        ActivitiesTable.update({
            (ActivitiesTable.kind inList listOf("thread.opened", "comment.added")) and
                ((ActivitiesTable.createdCommentId eq commentId) or (ActivitiesTable.legacyExcerptCommentId eq commentId))
        }) { it[summary] = "삭제된 댓글" }
    }

    // Mention rows go with their entries through the foreign key's cascade.
    override fun pruneBefore(cutoff: Instant): Int = ActivitiesTable.deleteWhere { occurredAt less cutoff.atOffset(ZoneOffset.UTC) }

    override fun find(query: ActivityQuery): List<ActivityEntry> {
        if (query.spaceIds?.isEmpty() == true) return emptyList()
        val select = ActivitiesTable.selectAll()
        query.spaceIds?.let { spaces -> select.andWhere { ActivitiesTable.spaceId inList spaces } }
        query.since?.let { since -> select.andWhere { ActivitiesTable.occurredAt greater since.atOffset(ZoneOffset.UTC) } }
        query.beforeId?.let { before -> select.andWhere { ActivitiesTable.id less before } }
        query.afterId?.let { after -> select.andWhere { ActivitiesTable.id greater after } }
        val order = if (query.oldestFirst) SortOrder.ASC else SortOrder.DESC
        return entries(select.orderBy(ActivitiesTable.id to order).limit(query.limit).toList())
    }

    override fun scan(scan: ActivityScan): List<ActivityEntry> {
        if (scan.kinds.isEmpty() && !scan.createdComments) return emptyList()
        val select = ActivitiesTable.selectAll().where { ActivitiesTable.occurredAt greaterEq scan.since.atOffset(ZoneOffset.UTC) }
        select.andWhere {
            val listed = ActivitiesTable.kind inList scan.kinds
            if (scan.createdComments) listed or ActivitiesTable.createdCommentId.isNotNull() else listed
        }
        scan.afterId?.let { after -> select.andWhere { ActivitiesTable.id greater after } }
        return entries(select.orderBy(ActivitiesTable.id to SortOrder.ASC).limit(scan.limit).toList())
    }

    private fun entries(rows: List<ResultRow>): List<ActivityEntry> {
        val commented = rows.filter { it[ActivitiesTable.createdCommentId] != null }.map { it[ActivitiesTable.id] }
        val mentions =
            if (commented.isEmpty()) {
                emptyMap()
            } else {
                ActivityMentionsTable
                    .selectAll()
                    .where { ActivityMentionsTable.activityId inList commented }
                    .toList()
                    .groupBy({ it[ActivityMentionsTable.activityId] }, { it[ActivityMentionsTable.login] })
            }
        return rows.map { it.toEntry(mentions[it[ActivitiesTable.id]].orEmpty()) }
    }

    private fun ResultRow.toEntry(mentions: List<String>): ActivityEntry {
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
                this[ActivitiesTable.createdCommentId]?.let { CreatedComment(it, mentions.toSet()) },
            )
        return ActivityEntry(this[ActivitiesTable.id], record)
    }
}
