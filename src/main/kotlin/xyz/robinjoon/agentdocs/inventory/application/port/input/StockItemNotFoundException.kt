package xyz.robinjoon.agentdocs.inventory.application.port.input

import xyz.robinjoon.agentdocs.inventory.domain.stock.Sku

class StockItemNotFoundException(
    sku: Sku,
) : RuntimeException("Stock item not found: $sku")
