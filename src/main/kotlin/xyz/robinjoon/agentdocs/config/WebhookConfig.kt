package xyz.robinjoon.agentdocs.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import xyz.robinjoon.agentdocs.application.port.output.ActivityCatalog
import xyz.robinjoon.agentdocs.application.port.output.ActivityReporter
import xyz.robinjoon.agentdocs.webhook.adapter.inbound.scheduling.WebhookScheduler
import xyz.robinjoon.agentdocs.webhook.adapter.outbound.crypto.SecureRandomWebhookSecretSource
import xyz.robinjoon.agentdocs.webhook.adapter.outbound.http.HttpWebhookGateway
import xyz.robinjoon.agentdocs.webhook.adapter.outbound.persistence.ExposedWebhookDeliveryRepository
import xyz.robinjoon.agentdocs.webhook.adapter.outbound.persistence.ExposedWebhookEndpointRepository
import xyz.robinjoon.agentdocs.webhook.application.model.WebhookSettings
import xyz.robinjoon.agentdocs.webhook.application.port.input.WebhookDispatchUseCase
import xyz.robinjoon.agentdocs.webhook.application.port.input.WebhookEndpointUseCase
import xyz.robinjoon.agentdocs.webhook.application.port.input.WebhookSweepUseCase
import xyz.robinjoon.agentdocs.webhook.application.port.output.WebhookDeliveryRepository
import xyz.robinjoon.agentdocs.webhook.application.port.output.WebhookEndpointRepository
import xyz.robinjoon.agentdocs.webhook.application.port.output.WebhookGateway
import xyz.robinjoon.agentdocs.webhook.application.port.output.WebhookSecretSource
import xyz.robinjoon.agentdocs.webhook.application.service.WebhookDeliveryLedger
import xyz.robinjoon.agentdocs.webhook.application.service.WebhookDeliveryPlanner
import xyz.robinjoon.agentdocs.webhook.application.service.WebhookDispatchService
import xyz.robinjoon.agentdocs.webhook.application.service.WebhookEndpointService
import xyz.robinjoon.agentdocs.webhook.application.service.WebhookSweepService
import java.time.Clock

// The webhook context reads the shared activity log through its public port only. The scheduler runs under the
// application's @EnableScheduling; with no endpoint registered it sends nothing.
@Configuration(proxyBeanMethods = false)
class WebhookConfig {
    @Bean
    fun webhookSettings(properties: AgentDocsProperties): WebhookSettings = WebhookSettings(properties.publicUrl.trimEnd('/'))

    @Bean
    fun webhookPersistence(): WebhookPersistence = WebhookPersistence()

    @Bean
    fun webhookGateway(): WebhookGateway = HttpWebhookGateway()

    @Bean
    fun webhookSecretSource(): WebhookSecretSource = SecureRandomWebhookSecretSource()

    @Bean
    fun webhookEndpointUseCase(
        persistence: WebhookPersistence,
        secrets: WebhookSecretSource,
        activity: ActivityReporter,
        clock: Clock,
    ): WebhookEndpointUseCase = WebhookEndpointService(persistence.endpoints, persistence.deliveries, secrets, activity, clock)

    @Bean
    fun webhookDeliveryPlanner(
        persistence: WebhookPersistence,
        catalog: ActivityCatalog,
        settings: WebhookSettings,
        clock: Clock,
    ): WebhookDeliveryPlanner = WebhookDeliveryPlanner(persistence.endpoints, persistence.deliveries, catalog, settings, clock)

    @Bean
    fun webhookSweepUseCase(
        planner: WebhookDeliveryPlanner,
        clock: Clock,
    ): WebhookSweepUseCase = WebhookSweepService(planner, clock)

    @Bean
    fun webhookDeliveryLedger(
        persistence: WebhookPersistence,
        activity: ActivityReporter,
    ): WebhookDeliveryLedger = WebhookDeliveryLedger(persistence.endpoints, persistence.deliveries, activity)

    @Bean
    fun webhookDispatchUseCase(
        ledger: WebhookDeliveryLedger,
        gateway: WebhookGateway,
        clock: Clock,
    ): WebhookDispatchUseCase = WebhookDispatchService(ledger, gateway, clock)

    @Bean
    fun webhookScheduler(
        sweep: WebhookSweepUseCase,
        dispatch: WebhookDispatchUseCase,
    ): WebhookScheduler = WebhookScheduler(sweep, dispatch)
}

// The webhook context's own tables, handed to its services together.
class WebhookPersistence {
    val endpoints: WebhookEndpointRepository = ExposedWebhookEndpointRepository()
    val deliveries: WebhookDeliveryRepository = ExposedWebhookDeliveryRepository()
}
