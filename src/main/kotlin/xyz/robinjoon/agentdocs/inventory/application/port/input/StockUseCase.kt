package xyz.robinjoon.agentdocs.inventory.application.port.input

import xyz.robinjoon.agentdocs.inventory.domain.stock.Sku
import xyz.robinjoon.agentdocs.inventory.domain.stock.StockItem

interface StockUseCase {
    // Fails with DuplicateSkuException when the SKU is already registered.
    fun register(
        sku: Sku,
        onHand: Int,
    ): StockItem

    fun restock(
        sku: Sku,
        quantity: Int,
    ): StockItem

    fun get(sku: Sku): StockItem
}
