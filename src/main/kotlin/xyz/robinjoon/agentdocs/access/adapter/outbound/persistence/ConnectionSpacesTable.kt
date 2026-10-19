package xyz.robinjoon.agentdocs.access.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

internal object ConnectionSpacesTable : Table("connection_spaces") {
    val connectionId = javaUUID("connection_id")
    val spaceId = javaUUID("space_id")

    override val primaryKey = PrimaryKey(connectionId, spaceId)
}
