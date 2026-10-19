package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import xyz.robinjoon.agentdocs.workspace.domain.space.ExternalLinkKind

internal object SpaceLinksTable : Table("space_links") {
    val spaceId = javaUUID("space_id")
    val position = integer("link_position")
    val kind = enumerationByName("kind", 30, ExternalLinkKind::class)
    val value = varchar("link_value", 2000)

    override val primaryKey = PrimaryKey(spaceId, position)
}
