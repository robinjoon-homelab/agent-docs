package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

internal object SpaceFormerSlugsTable : Table("space_former_slugs") {
    val slug = varchar("slug", 50)
    val spaceId = javaUUID("space_id")
    val position = integer("slug_position")

    override val primaryKey = PrimaryKey(slug)
}
