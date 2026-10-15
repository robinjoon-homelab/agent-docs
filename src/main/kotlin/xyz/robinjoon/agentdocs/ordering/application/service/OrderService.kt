package xyz.robinjoon.agentdocs.ordering.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.ordering.application.port.input.OfferNotOnSaleException
import xyz.robinjoon.agentdocs.ordering.application.port.input.OrderNotFoundException
import xyz.robinjoon.agentdocs.ordering.application.port.input.OrderUseCase
import xyz.robinjoon.agentdocs.ordering.application.port.output.OfferRepository
import xyz.robinjoon.agentdocs.ordering.application.port.output.OrderRepository
import xyz.robinjoon.agentdocs.ordering.domain.Sku
import xyz.robinjoon.agentdocs.ordering.domain.offer.OfferStatus
import xyz.robinjoon.agentdocs.ordering.domain.order.Order
import xyz.robinjoon.agentdocs.ordering.domain.order.OrderId
import xyz.robinjoon.agentdocs.ordering.domain.order.OrderLine
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Transactional(readOnly = true)
class OrderService(
    private val repository: OrderRepository,
    private val offers: OfferRepository,
    private val clock: Clock,
) : OrderUseCase {
    // The order validates its own lines before any offer is read, and only the order is written. Being on sale is
    // checked against an unlocked read, not kept as an invariant: a discontinuation that commits after this read
    // does not undo the order.
    @Transactional
    override fun place(lines: List<OrderLine>): Order {
        val order = Order.place(OrderId(UUID.randomUUID()), lines, Instant.now(clock))
        requireOnSale(order.lines.map { it.sku }.toSet())
        return repository.add(order)
    }

    override fun get(id: OrderId): Order = repository.findById(id) ?: throw OrderNotFoundException(id)

    // The row lock serializes concurrent cancellations, so only the first one writes. The others read the
    // committed cancellation and return it unchanged.
    @Transactional
    override fun cancel(id: OrderId): Order {
        val order = repository.findByIdForUpdate(id) ?: throw OrderNotFoundException(id)
        val cancelled = order.cancel() ?: return order
        return repository.update(cancelled)
    }

    private fun requireOnSale(skus: Set<Sku>) {
        val onSale = offers.findBySkus(skus).filter { it.status == OfferStatus.ON_SALE }.map { it.sku }
        val notOnSale = skus - onSale.toSet()
        if (notOnSale.isNotEmpty()) {
            throw OfferNotOnSaleException(notOnSale)
        }
    }
}
