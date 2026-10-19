package xyz.robinjoon.agentdocs.workspace.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.workspace.application.model.UsageReport
import xyz.robinjoon.agentdocs.workspace.application.port.input.UsageUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.UsageCatalog
import xyz.robinjoon.agentdocs.workspace.domain.Access
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

// Computed from the platform's own tables when asked; nothing leaves the server.
@Transactional(readOnly = true)
class UsageService(
    private val catalog: UsageCatalog,
    private val spaces: SpaceRepository,
    private val clock: Clock,
) : UsageUseCase {
    override fun report(access: Access): UsageReport {
        Permissions.requireOwner(access, "reading usage metrics")
        val thisWeek = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val start = thisWeek.minusWeeks(WEEKS - 1).atStartOfDay().toInstant(ZoneOffset.UTC)
        val facts = catalog.since(start)
        return UsageReport(facts.weeks, spaces.findAll(), median(facts.agentResponseTimes), facts.revisitedDocuments)
    }

    private fun median(times: List<Duration>): Duration? {
        if (times.isEmpty()) return null
        val sorted = times.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else sorted[middle - 1].plus(sorted[middle]).dividedBy(2)
    }

    private companion object {
        const val WEEKS = 4L
    }
}
