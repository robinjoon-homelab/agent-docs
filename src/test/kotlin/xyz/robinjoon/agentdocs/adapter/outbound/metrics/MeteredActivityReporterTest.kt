package xyz.robinjoon.agentdocs.adapter.outbound.metrics

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.application.model.ActivityActor
import xyz.robinjoon.agentdocs.application.model.ActivityActorKind
import xyz.robinjoon.agentdocs.application.model.ActivityRecord
import xyz.robinjoon.agentdocs.application.model.ActivitySubject
import xyz.robinjoon.agentdocs.application.port.output.ActivityReporter
import java.time.Instant
import java.util.UUID

class MeteredActivityReporterTest {
    private val log = mockk<ActivityReporter>()
    private val meters = SimpleMeterRegistry()
    private val reporter = MeteredActivityReporter(log, meters)
    private val agent = ActivityActor(ActivityActorKind.AGENT, UUID.randomUUID(), "robinjoon", UUID.randomUUID(), "Codex (chatgpt.com)")

    @Test
    fun `each recorded write is counted by its kind and actor kind`() {
        every { log.record(any()) } just runs

        repeat(2) { reporter.record(record("document.revised", agent)) }
        reporter.record(record("document.revised", ActivityActor.system))

        assertThat(meters.counter("agentdocs.writes", "kind", "document.revised", "actor", "agent").count()).isEqualTo(2.0)
        assertThat(meters.counter("agentdocs.writes", "kind", "document.revised", "actor", "system").count()).isEqualTo(1.0)
    }

    private fun record(
        kind: String,
        actor: ActivityActor,
    ): ActivityRecord = ActivityRecord(kind, actor, ActivitySubject(null, null, null, null), "rev 2", Instant.parse("2026-10-20T00:00:00Z"))
}
