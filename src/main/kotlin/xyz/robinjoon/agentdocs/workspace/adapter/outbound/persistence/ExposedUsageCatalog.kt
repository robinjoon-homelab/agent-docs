package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.select
import xyz.robinjoon.agentdocs.workspace.application.model.UsageFacts
import xyz.robinjoon.agentdocs.workspace.application.model.WeeklyUsage
import xyz.robinjoon.agentdocs.workspace.application.port.output.UsageCatalog
import xyz.robinjoon.agentdocs.workspace.domain.ActorKind
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import java.util.UUID

// Reads the rows of the period and counts in memory. One person's few spaces produce a few thousand rows a month,
// which keeps this simpler than per-database date functions.
class ExposedUsageCatalog : UsageCatalog {
    override fun since(start: Instant): UsageFacts {
        val revisions =
            DocumentRevisionsTable
                .select(DocumentRevisionsTable.spaceId, DocumentRevisionsTable.documentId, DocumentRevisionsTable.createdAt)
                .where { DocumentRevisionsTable.createdAt greaterEq start.utc() }
                .map {
                    Triple(
                        it[DocumentRevisionsTable.spaceId],
                        it[DocumentRevisionsTable.documentId],
                        it[DocumentRevisionsTable.createdAt].toInstant(),
                    )
                }
        val comments =
            ThreadCommentsTable
                .join(ThreadsTable, JoinType.INNER, ThreadCommentsTable.threadId, ThreadsTable.id)
                .select(ThreadsTable.spaceId, ThreadCommentsTable.createdAt)
                .where { ThreadCommentsTable.createdAt greaterEq start.utc() }
                .map { it[ThreadsTable.spaceId] to it[ThreadCommentsTable.createdAt].toInstant() }
        return UsageFacts(weeks(revisions, comments), agentResponseTimes(start), revisited(start))
    }

    private fun weeks(
        revisions: List<Triple<UUID, UUID, Instant>>,
        comments: List<Pair<UUID, Instant>>,
    ): List<WeeklyUsage> {
        val documentCounts = revisions.groupBy({ (space, _, at) -> space to weekOf(at) }, { it.second }).mapValues { it.value.toSet().size }
        val commentCounts = comments.groupingBy { (space, at) -> space to weekOf(at) }.eachCount()
        return (documentCounts.keys + commentCounts.keys)
            .sortedWith(compareBy({ it.second }, { it.first }))
            .map { key -> WeeklyUsage(key.second, SpaceId(key.first), documentCounts[key] ?: 0, commentCounts[key] ?: 0) }
    }

    // For threads people opened in the period: the time until the first comment an agent wrote in them.
    private fun agentResponseTimes(start: Instant): List<Duration> {
        val opened =
            ThreadsTable
                .select(ThreadsTable.id, ThreadsTable.createdAt)
                .where { (ThreadsTable.authorKind eq ActorKind.HUMAN) and (ThreadsTable.createdAt greaterEq start.utc()) }
                .associate { it[ThreadsTable.id] to it[ThreadsTable.createdAt].toInstant() }
        if (opened.isEmpty()) return emptyList()
        val firstAnswers =
            ThreadCommentsTable
                .select(ThreadCommentsTable.threadId, ThreadCommentsTable.createdAt)
                .where { (ThreadCommentsTable.threadId inList opened.keys) and (ThreadCommentsTable.author.kind eq ActorKind.AGENT) }
                .groupBy({ it[ThreadCommentsTable.threadId] }, { it[ThreadCommentsTable.createdAt].toInstant() })
                .mapValues { (_, times) -> times.min() }
        return firstAnswers.mapNotNull { (thread, answeredAt) -> opened[thread]?.let { Duration.between(it, answeredAt) } }
    }

    // Documents a person opened on at least two different days of the period.
    private fun revisited(start: Instant): Int =
        DocumentViewsTable
            .select(DocumentViewsTable.documentId, DocumentViewsTable.viewerLogin, DocumentViewsTable.viewedOn)
            .where { DocumentViewsTable.viewedOn greaterEq LocalDate.ofInstant(start, ZoneOffset.UTC) }
            .groupingBy { it[DocumentViewsTable.documentId] to it[DocumentViewsTable.viewerLogin] }
            .eachCount()
            .filterValues { it >= 2 }
            .keys
            .map { it.first }
            .distinct()
            .size

    private fun weekOf(at: Instant): LocalDate =
        LocalDate.ofInstant(at, ZoneOffset.UTC).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}
