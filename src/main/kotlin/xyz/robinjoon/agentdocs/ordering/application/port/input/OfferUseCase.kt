package xyz.robinjoon.agentdocs.ordering.application.port.input

import xyz.robinjoon.agentdocs.ordering.domain.Sku
import xyz.robinjoon.agentdocs.ordering.domain.offer.Offer
import xyz.robinjoon.agentdocs.ordering.domain.offer.OfferId

interface OfferUseCase {
    // Fails with DuplicateOfferException when the SKU already has an Offer.
    fun register(sku: Sku): Offer

    fun get(id: OfferId): Offer

    // Repeating the request returns the discontinued offer unchanged. Existing orders are not touched.
    fun discontinue(id: OfferId): Offer
}
