package xyz.robinjoon.agentdocs.workspace.application

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.ownerAccess
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.space
import xyz.robinjoon.agentdocs.workspace.application.model.UsageFacts
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.UsageCatalog
import xyz.robinjoon.agentdocs.workspace.application.service.UsageService
import java.time.Duration
import java.time.Instant

class UsageServiceTest {
    private val catalog = mockk<UsageCatalog>()
    private val spaces = mockk<SpaceRepository>()
    private val service = UsageService(catalog, spaces, WorkspaceFixtures.clock)

    @Test
    fun `the report covers four weeks from a Monday and takes the median agent response`() {
        val times = listOf(Duration.ofMinutes(30), Duration.ofMinutes(10), Duration.ofMinutes(20), Duration.ofMinutes(40))
        every { catalog.since(Instant.parse("2026-09-21T00:00:00Z")) } returns UsageFacts(emptyList(), times, 2)
        every { spaces.findAll() } returns listOf(space)

        val report = service.report(ownerAccess)

        assertThat(report.agentResponseMedian).isEqualTo(Duration.ofMinutes(25))
        assertThat(report.revisitedDocuments).isEqualTo(2)
    }

    @Test
    fun `no agent responses gives no median`() {
        every { catalog.since(any()) } returns UsageFacts(emptyList(), emptyList(), 0)
        every { spaces.findAll() } returns emptyList()

        assertThat(service.report(ownerAccess).agentResponseMedian).isNull()
    }
}
