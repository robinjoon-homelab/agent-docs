package xyz.robinjoon.agentdocs.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.application.port.input.ActivityRetentionUseCase
import xyz.robinjoon.agentdocs.application.port.output.ActivityReporter
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Transactional(readOnly = true)
class ActivityRetentionService(
    private val reporter: ActivityReporter,
    private val clock: Clock,
) : ActivityRetentionUseCase {
    @Transactional
    override fun prune(): Int = reporter.pruneBefore(Instant.now(clock).minus(retention))

    private companion object {
        val retention: Duration = Duration.ofDays(365)
    }
}
