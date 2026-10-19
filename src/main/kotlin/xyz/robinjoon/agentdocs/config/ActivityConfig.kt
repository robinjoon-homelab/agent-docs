package xyz.robinjoon.agentdocs.config

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import xyz.robinjoon.agentdocs.adapter.outbound.metrics.MeteredActivityReporter
import xyz.robinjoon.agentdocs.adapter.outbound.persistence.ExposedActivityLog
import xyz.robinjoon.agentdocs.application.port.input.ActivityRetentionUseCase
import xyz.robinjoon.agentdocs.application.port.output.ActivityReporter
import xyz.robinjoon.agentdocs.application.service.ActivityRetentionService
import java.time.Clock

// The shared activity log. One adapter serves both its ports, so writers and readers see the same table; writers reach
// it through the counter of writes.
@Configuration(proxyBeanMethods = false)
class ActivityConfig {
    @Bean
    fun activityLog(): ExposedActivityLog = ExposedActivityLog()

    @Bean
    @Primary
    fun activityReporter(
        log: ExposedActivityLog,
        meters: MeterRegistry,
    ): ActivityReporter = MeteredActivityReporter(log, meters)

    @Bean
    fun activityRetentionUseCase(
        reporter: ActivityReporter,
        clock: Clock,
    ): ActivityRetentionUseCase = ActivityRetentionService(reporter, clock)
}
