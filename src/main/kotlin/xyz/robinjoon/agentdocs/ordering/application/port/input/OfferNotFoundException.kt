package xyz.robinjoon.agentdocs.ordering.application.port.input

import xyz.robinjoon.agentdocs.ordering.domain.offer.OfferId

class OfferNotFoundException(
    id: OfferId,
) : RuntimeException("Offer not found: $id")
