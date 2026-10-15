package xyz.robinjoon.agentdocs.ordering.application.port.output

import xyz.robinjoon.agentdocs.ordering.domain.order.Order
import xyz.robinjoon.agentdocs.ordering.domain.order.OrderId

// add and update return the stored state so callers see the database timestamp precision.
interface OrderRepository {
    // Stores the order and its lines together. Fails instead of overwriting when the id already exists.
    fun add(order: Order): Order

    // Writes only the status, because lines never change after placement. Fails instead of inserting when the
    // order no longer exists.
    fun update(order: Order): Order

    fun findById(id: OrderId): Order?

    // Locks the order row until the caller's writable transaction ends, so changes to the same order apply
    // one after the other.
    fun findByIdForUpdate(id: OrderId): Order?
}
