package xyz.robinjoon.agentdocs.application

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.application.port.output.ActivityReporter
import xyz.robinjoon.agentdocs.application.service.ActivityRetentionService
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ActivityRetentionServiceTest {
    @Test
    fun `entries older than a year from now are pruned`() {
        val reporter = mockk<ActivityReporter>()
        every { reporter.pruneBefore(Instant.parse("2025-10-18T09:00:00Z")) } returns 3

        val pruned = ActivityRetentionService(reporter, Clock.fixed(Instant.parse("2026-10-18T09:00:00Z"), ZoneOffset.UTC)).prune()

        assertThat(pruned).isEqualTo(3)
    }
}
