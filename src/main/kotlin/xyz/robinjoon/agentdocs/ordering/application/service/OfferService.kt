package xyz.robinjoon.agentdocs.ordering.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.ordering.application.port.input.OfferNotFoundException
import xyz.robinjoon.agentdocs.ordering.application.port.input.OfferUseCase
import xyz.robinjoon.agentdocs.ordering.application.port.output.OfferRepository
import xyz.robinjoon.agentdocs.ordering.domain.Sku
import xyz.robinjoon.agentdocs.ordering.domain.offer.Offer
import xyz.robinjoon.agentdocs.ordering.domain.offer.OfferId
import java.util.UUID

@Transactional(readOnly = true)
class OfferService(
    private val repository: OfferRepository,
) : OfferUseCase {
    // The stored SKU key alone decides uniqueness, so a sequential and a concurrent duplicate fail the same way.
    @Transactional
    override fun register(sku: Sku): Offer = repository.add(Offer.register(OfferId(UUID.randomUUID()), sku))

    override fun get(id: OfferId): Offer = repository.findById(id) ?: throw OfferNotFoundException(id)

    // The row lock serializes concurrent discontinuations, so only the first one writes. Orders are never read or
    // written here.
    @Transactional
    override fun discontinue(id: OfferId): Offer {
        val offer = repository.findByIdForUpdate(id) ?: throw OfferNotFoundException(id)
        val discontinued = offer.discontinue() ?: return offer
        return repository.update(discontinued)
    }
}
