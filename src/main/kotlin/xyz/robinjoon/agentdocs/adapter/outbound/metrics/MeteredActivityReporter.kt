package xyz.robinjoon.agentdocs.adapter.outbound.metrics

import io.micrometer.core.instrument.MeterRegistry
import xyz.robinjoon.agentdocs.application.model.ActivityRecord
import xyz.robinjoon.agentdocs.application.port.output.ActivityReporter
import java.time.Instant

// Every write is recorded in the activity log, so counting what passes through gives the write counts by actor kind
// (agentdocs.writes). A write whose transaction rolls back after it was recorded is still counted.
class MeteredActivityReporter(
    private val log: ActivityReporter,
    private val meters: MeterRegistry,
) : ActivityReporter {
    override fun record(record: ActivityRecord) {
        log.record(record)
        meters
            .counter(
                WRITES,
                "kind",
                record.kind,
                "actor",
                record.actor.kind.name
                    .lowercase(),
            ).increment()
    }

    override fun pruneBefore(cutoff: Instant): Int = log.pruneBefore(cutoff)

    private companion object {
        const val WRITES = "agentdocs.writes"
    }
}
