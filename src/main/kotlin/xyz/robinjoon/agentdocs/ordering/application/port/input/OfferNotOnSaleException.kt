package xyz.robinjoon.agentdocs.ordering.application.port.input

import xyz.robinjoon.agentdocs.ordering.domain.Sku

// The SKUs of an order that have no Offer or only a discontinued one, sorted so the message is stable.
class OfferNotOnSaleException(
    skus: Set<Sku>,
) : RuntimeException("Not on sale: ${skus.map { it.value }.sorted().joinToString()}") {
    val skus: Set<Sku> = skus.toSet()
}
