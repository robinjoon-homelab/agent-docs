package xyz.robinjoon.agentdocs.ordering.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import xyz.robinjoon.agentdocs.ordering.domain.offer.OfferStatus

internal object OffersTable : Table("offers") {
    val id = javaUUID("id")
    val sku = varchar("sku", 64)
    val status = enumerationByName("status", 20, OfferStatus::class)

    override val primaryKey = PrimaryKey(id)
}
