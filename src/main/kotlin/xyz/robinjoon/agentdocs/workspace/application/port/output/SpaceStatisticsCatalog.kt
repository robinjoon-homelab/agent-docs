package xyz.robinjoon.agentdocs.workspace.application.port.output

import xyz.robinjoon.agentdocs.workspace.application.model.SpaceStatistics
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId

interface SpaceStatisticsCatalog {
    // Counts documents outside the trash. Spaces without documents are absent from the result.
    fun forSpaces(ids: Set<SpaceId>): Map<SpaceId, SpaceStatistics>
}
