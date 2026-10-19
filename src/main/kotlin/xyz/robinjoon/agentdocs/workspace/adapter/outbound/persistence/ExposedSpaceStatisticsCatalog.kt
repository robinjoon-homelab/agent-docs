package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.select
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceStatistics
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceStatisticsCatalog
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId

class ExposedSpaceStatisticsCatalog : SpaceStatisticsCatalog {
    override fun forSpaces(ids: Set<SpaceId>): Map<SpaceId, SpaceStatistics> {
        if (ids.isEmpty()) return emptyMap()
        val documents = DocumentsTable.id.count()
        val latest = DocumentsTable.updatedAt.max()
        return DocumentsTable
            .select(DocumentsTable.spaceId, documents, latest)
            .where { (DocumentsTable.spaceId inList ids.map { it.value }) and DocumentsTable.trashedAt.isNull() }
            .groupBy(DocumentsTable.spaceId)
            .associate { SpaceId(it[DocumentsTable.spaceId]) to SpaceStatistics(it[documents].toInt(), it[latest]?.toInstant()) }
    }
}
