package xyz.robinjoon.agentdocs.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import xyz.robinjoon.agentdocs.ordering.adapter.outbound.persistence.ExposedOfferRepository
import xyz.robinjoon.agentdocs.ordering.adapter.outbound.persistence.ExposedOrderRepository
import xyz.robinjoon.agentdocs.ordering.application.port.input.OfferUseCase
import xyz.robinjoon.agentdocs.ordering.application.port.input.OrderUseCase
import xyz.robinjoon.agentdocs.ordering.application.port.output.OfferRepository
import xyz.robinjoon.agentdocs.ordering.application.port.output.OrderRepository
import xyz.robinjoon.agentdocs.ordering.application.service.OfferService
import xyz.robinjoon.agentdocs.ordering.application.service.OrderService
import java.time.Clock

@Configuration(proxyBeanMethods = false)
class OrderingConfig {
    @Bean
    fun orderRepository(): OrderRepository = ExposedOrderRepository()

    @Bean
    fun offerRepository(): OfferRepository = ExposedOfferRepository()

    @Bean
    fun orderUseCase(
        repository: OrderRepository,
        offers: OfferRepository,
        clock: Clock,
    ): OrderUseCase = OrderService(repository, offers, clock)

    @Bean
    fun offerUseCase(repository: OfferRepository): OfferUseCase = OfferService(repository)
}
