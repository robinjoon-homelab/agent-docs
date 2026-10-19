package xyz.robinjoon.agentdocs.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import xyz.robinjoon.agentdocs.adapter.inbound.scheduling.ActivityRetentionScheduler
import xyz.robinjoon.agentdocs.application.port.input.ActivityRetentionUseCase
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.scheduling.TrashCleanupScheduler
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase

@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfig {
    @Bean
    fun trashCleanupScheduler(documents: DocumentUseCase): TrashCleanupScheduler = TrashCleanupScheduler(documents)

    @Bean
    fun activityRetentionScheduler(retention: ActivityRetentionUseCase): ActivityRetentionScheduler = ActivityRetentionScheduler(retention)
}
