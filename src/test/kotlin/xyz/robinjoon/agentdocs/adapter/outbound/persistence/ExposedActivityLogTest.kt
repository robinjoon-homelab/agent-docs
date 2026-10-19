package xyz.robinjoon.agentdocs.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.application.model.ActivityActor
import xyz.robinjoon.agentdocs.application.model.ActivityActorKind
import xyz.robinjoon.agentdocs.application.model.ActivityQuery
import xyz.robinjoon.agentdocs.application.model.ActivityRecord
import xyz.robinjoon.agentdocs.application.model.ActivitySubject
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

        val newest = database.inTransaction { log.find(ActivityQuery(null, null, null, 10, oldestFirst = false)) }
        val fromMoment = database.inTransaction { log.find(ActivityQuery(null, at.plusSeconds(5), null, 10, oldestFirst = true)) }
        val inSpace = database.inTransaction { log.find(ActivityQuery(setOf(space), null, newest.first().id, 10, oldestFirst = false)) }

        assertThat(newest.map { it.record }).containsExactly(third, second, first)
        assertThat(fromMoment.map { it.record }).containsExactly(second, third)
        assertThat(inSpace.map { it.record }).containsExactly(first)
    }

    @Test
    fun `pruning removes only entries older than the cutoff`() {
        database.inTransaction { listOf(record(space, 0), record(space, 100)).forEach(log::record) }

        assertThat(database.inTransaction { log.pruneBefore(at.plusSeconds(50)) }).isEqualTo(1)
        assertThat(database.inTransaction { log.find(ActivityQuery(null, null, null, 10, oldestFirst = true)) }).hasSize(1)
    }

    private fun record(
        spaceId: UUID?,
        offset: Long,
    ): ActivityRecord =
        ActivityRecord("document.revised", agent, ActivitySubject(spaceId, UUID.randomUUID(), null, 2), "블록 1개 수정", at.plusSeconds(offset))
}
