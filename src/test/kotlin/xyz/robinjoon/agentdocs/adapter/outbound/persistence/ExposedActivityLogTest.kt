package xyz.robinjoon.agentdocs.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.application.model.ActivityActor
import xyz.robinjoon.agentdocs.application.model.ActivityActorKind
import xyz.robinjoon.agentdocs.application.model.ActivityFilter
import xyz.robinjoon.agentdocs.application.model.ActivityQuery
import xyz.robinjoon.agentdocs.application.model.ActivityRecord
import xyz.robinjoon.agentdocs.application.model.ActivityScan
import xyz.robinjoon.agentdocs.application.model.ActivitySubject
import xyz.robinjoon.agentdocs.application.model.CreatedComment
import xyz.robinjoon.agentdocs.support.persistence.MigratedH2Database
import java.time.Instant
import java.util.UUID

class ExposedActivityLogTest {
    private val database = MigratedH2Database()
    private val log = ExposedActivityLog()
    private val at = Instant.parse("2026-10-18T09:00:00.123456Z")
    private val space = UUID.randomUUID()
    private val agent = ActivityActor(ActivityActorKind.AGENT, UUID.randomUUID(), "robin", UUID.randomUUID(), "Claude Code")

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `entries round trip and come newest first, oldest first from a moment, or within spaces`() {
        val first = record(space, 0)
        val second = record(null, 10)
        val third = record(space, 20)
        database.inTransaction { listOf(first, second, third).forEach(log::record) }

        val newest = database.inTransaction { log.find(ActivityQuery(null, null, 10, oldestFirst = false)) }
        val fromMoment =
            database.inTransaction {
                log.find(ActivityQuery(null, null, 10, oldestFirst = true, filter = ActivityFilter(since = at.plusSeconds(5))))
            }
        val inSpace = database.inTransaction { log.find(ActivityQuery(setOf(space), newest.first().id, 10, oldestFirst = false)) }

        assertThat(newest.map { it.record }).containsExactly(third, second, first)
        assertThat(fromMoment.map { it.record }).containsExactly(second, third)
        assertThat(inSpace.map { it.record }).containsExactly(first)
    }

    @Test
    fun `activity filters match older entries before limiting the result and keep the space scope`() {
        val purged = record(space, 1).copy(kind = "document.purged", actor = ActivityActor.system)
        val outside = purged.copy(subject = purged.subject.copy(spaceId = UUID.randomUUID()))
        val membership = purged.copy(subject = purged.subject.copy(membershipId = UUID.randomUUID()))
        database.inTransaction {
            log.record(purged.copy(at = at))
            log.record(purged)
            log.record(purged.copy(at = at.plusSeconds(2)))
            log.record(purged.copy(actor = agent))
            log.record(outside)
            log.record(membership)
            repeat(21) { log.record(record(space, 10L + it).copy(actor = ActivityActor.system)) }
        }
        val query = ActivityQuery(setOf(space), null, 20, false, filter = ActivityFilter("document.purged", ActivityActorKind.SYSTEM, at))

        val unfiltered = database.inTransaction { log.find(query.copy(filter = ActivityFilter())) }
        val filtered = database.inTransaction { log.find(query) }

        assertThat(unfiltered.map { it.record.kind }).containsOnly("document.revised").hasSize(20)
        assertThat(filtered.map { it.record }).containsExactly(purged.copy(at = at.plusSeconds(2)), purged)
    }

    @Test
    fun `pruning removes only entries older than the cutoff`() {
        database.inTransaction { listOf(record(space, 0), record(space, 100)).forEach(log::record) }

        assertThat(database.inTransaction { log.pruneBefore(at.plusSeconds(50)) }).isEqualTo(1)
        assertThat(database.inTransaction { log.find(ActivityQuery(null, null, 10, oldestFirst = true)) }).hasSize(1)
    }

    @Test
    fun `membership targets appear in the instance log only`() {
        val target = UUID.randomUUID()
        val member = record(space, 1).copy(kind = "membership.invited", subject = ActivitySubject(space, null, null, null, target))
        val change = record(space, 0)
        database.inTransaction { listOf(change, member).forEach(log::record) }

        val instance = database.inTransaction { log.find(ActivityQuery(null, null, 10, oldestFirst = false)) }
        val scoped = database.inTransaction { log.find(ActivityQuery(setOf(space), null, 10, oldestFirst = false)) }
        assertThat(instance.map { it.record }).containsExactly(member, change)
        assertThat(scoped.map { it.record }).containsExactly(change)
    }

    @Test
    fun `the instance log distinguishes identically named connections and keeps older entries without a target`() {
        val owner = ActivityActor(ActivityActorKind.HUMAN, UUID.randomUUID(), "robin", null, null)
        val subject = ActivitySubject(null, null, null, null)
        val legacy = ActivityRecord("connection.revoked", owner, subject, "연결 회수: 빌드", at)
        val first = legacy.copy(subject = subject.copy(connectionId = UUID.randomUUID()))
        val second = legacy.copy(subject = subject.copy(connectionId = UUID.randomUUID()))
        database.inTransaction { listOf(legacy, first, second).forEach(log::record) }

        val instance = database.inTransaction { log.find(ActivityQuery(null, null, 10, oldestFirst = true)) }
        val scoped = database.inTransaction { log.find(ActivityQuery(setOf(space), null, 10, oldestFirst = true)) }

        assertThat(instance.map { it.record }).containsExactly(legacy, first, second)
        assertThat(scoped).isEmpty()
    }

    @Test
    fun `id cursor preserves same timestamp page boundary and initial since and space filters`() {
        database.inTransaction {
            log.record(record(space, -1))
            log.record(record(null, 0))
            repeat(101) { log.record(record(space, 0)) }
        }
        val since = at.minusSeconds(1)
        val first =
            database.inTransaction {
                log.find(
                    ActivityQuery(setOf(space), null, 100, true, filter = ActivityFilter(since = since)),
                )
            }
        val second =
            database.inTransaction {
                log.find(ActivityQuery(setOf(space), null, 100, true, afterId = first.last().id, filter = ActivityFilter(since = since)))
            }
        val end =
            database.inTransaction {
                log.find(ActivityQuery(setOf(space), null, 100, true, afterId = second.last().id, filter = ActivityFilter(since = since)))
            }

        assertThat(first).hasSize(100)
        assertThat(second).hasSize(1)
        assertThat((first + second).map { it.id }).doesNotHaveDuplicates().isSorted()
        assertThat((first + second).map { it.record.at }).containsOnly(at)
        assertThat((first + second).map { it.record.subject.spaceId }).containsOnly(space)
        assertThat(end).isEmpty()
    }

    @Test
    fun `an entry that created a comment keeps the comment id and its mentions`() {
        val commented = comment(0, setOf("alice", "bob"))
        val quiet = comment(1, emptySet())
        val plain = record(space, 2)
        database.inTransaction { listOf(commented, quiet, plain).forEach(log::record) }

        val read = database.inTransaction { log.find(ActivityQuery(null, null, 10, oldestFirst = true)) }

        assertThat(read.map { it.record }).containsExactly(commented, quiet, plain)
        assertThat(read[0].record.createdComment?.mentions).containsExactlyInAnyOrder("alice", "bob")
        assertThat(read[2].record.createdComment).isNull()
    }

    @Test
    fun `redacting an excerpt keeps the activity subject actor time and mentions`() {
        val deleted = comment(0, setOf("alice"))
        val live = comment(1, setOf("bob"))
        val id = requireNotNull(deleted.createdComment).id
        database.inTransaction {
            log.record(deleted)
            log.record(live)
            log.redactCommentExcerpt(id)
        }

        val read = database.inTransaction { log.find(ActivityQuery(null, null, 10, oldestFirst = true)) }

        assertThat(read.map { it.record }).containsExactly(deleted.copy(summary = "삭제된 댓글"), live)
    }

    @Test
    fun `a scan rereads listed kinds and created comments from a moment on in id order`() {
        val confirmed = record(space, 0).copy(kind = "document.revision_confirmed")
        val before = record(space, -10).copy(kind = "document.revision_confirmed")
        val commented = comment(5, setOf("alice"))
        database.inTransaction { listOf(before, confirmed, record(space, 1), commented).forEach(log::record) }
        val scan = ActivityScan(at, setOf("document.revision_confirmed"), createdComments = true, afterId = null, limit = 10)

        val all = database.inTransaction { log.scan(scan) }
        val after = database.inTransaction { log.scan(scan.copy(afterId = all.first().id)) }
        val kindsOnly = database.inTransaction { log.scan(scan.copy(createdComments = false)) }

        assertThat(all.map { it.record }).containsExactly(confirmed, commented)
        assertThat(after.map { it.record }).containsExactly(commented)
        assertThat(kindsOnly.map { it.record }).containsExactly(confirmed)
    }

    @Test
    fun `pruning an entry also drops its mentions`() {
        database.inTransaction { log.record(comment(0, setOf("alice"))) }

        assertThat(database.inTransaction { log.pruneBefore(at.plusSeconds(1)) }).isEqualTo(1)
        assertThat(database.inTransaction { log.find(ActivityQuery(null, null, 10, oldestFirst = true)) }).isEmpty()
    }

    private fun comment(
        offset: Long,
        mentions: Set<String>,
    ): ActivityRecord =
        ActivityRecord(
            "comment.added",
            agent,
            ActivitySubject(space, UUID.randomUUID(), UUID.randomUUID(), null),
            "문서: 댓글",
            at.plusSeconds(offset),
            CreatedComment(UUID.randomUUID(), mentions),
        )

    private fun record(
        spaceId: UUID?,
        offset: Long,
    ): ActivityRecord =
        ActivityRecord("document.revised", agent, ActivitySubject(spaceId, UUID.randomUUID(), null, 2), "블록 1개 수정", at.plusSeconds(offset))
}
