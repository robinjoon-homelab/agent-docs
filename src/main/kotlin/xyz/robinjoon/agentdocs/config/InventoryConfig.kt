package xyz.robinjoon.agentdocs.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import xyz.robinjoon.agentdocs.inventory.adapter.outbound.persistence.ExposedStockItemRepository
import xyz.robinjoon.agentdocs.inventory.application.port.input.StockUseCase
import xyz.robinjoon.agentdocs.inventory.application.port.output.StockItemRepository
import xyz.robinjoon.agentdocs.inventory.application.service.StockService

@Configuration(proxyBeanMethods = false)
class InventoryConfig {
    @Bean
    fun stockItemRepository(): StockItemRepository = ExposedStockItemRepository()

    @Bean
    fun stockUseCase(repository: StockItemRepository): StockUseCase = StockService(repository)
}
