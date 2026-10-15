package xyz.robinjoon.agentdocs.ordering.application.port.input

import xyz.robinjoon.agentdocs.ordering.domain.order.OrderId

class OrderNotFoundException(
    id: OrderId,
) : RuntimeException("Order not found: $id")
