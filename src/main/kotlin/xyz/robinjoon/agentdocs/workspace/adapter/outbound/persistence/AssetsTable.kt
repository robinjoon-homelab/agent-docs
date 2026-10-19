package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetMediaType

internal object AssetsTable : Table("assets") {
    val id = javaUUID("id")
    val spaceId = javaUUID("space_id")
    val mediaType = enumerationByName("media_type", 10, AssetMediaType::class)
    val sizeBytes = long("size_bytes")
    val sha256 = varchar("sha256", 64)
    val fileName = varchar("file_name", 200)
    val sourceUrl = varchar("source_url", 2000).nullable()
    val uploader = ActorColumns(this, "uploader")
    val uploadedAt = timestampWithTimeZone("uploaded_at")

    override val primaryKey = PrimaryKey(id)
}
