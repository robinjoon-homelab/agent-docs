package xyz.robinjoon.agentdocs.ordering.application.port.input

import xyz.robinjoon.agentdocs.ordering.domain.order.Order
import xyz.robinjoon.agentdocs.ordering.domain.order.OrderId
import xyz.robinjoon.agentdocs.ordering.domain.order.OrderLine

interface OrderUseCase {
    // Accepts the order only when every SKU has an Offer on sale. Fails with OfferNotOnSaleException otherwise.
    // Inventory is never consulted.
    fun place(lines: List<OrderLine>): Order

    fun get(id: OrderId): Order

    // Repeating the request returns the cancelled order unchanged.
    fun cancel(id: OrderId): Order
}
