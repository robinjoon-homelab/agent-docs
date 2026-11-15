package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID

object DocumentAssetBindingsTable : Table("document_asset_bindings") {
    val documentId = javaUUID("document_id")
    val referencedAssetId = javaUUID("referenced_asset_id")
    val storedAssetId = javaUUID("stored_asset_id")
    override val primaryKey = PrimaryKey(documentId, referencedAssetId)
}
