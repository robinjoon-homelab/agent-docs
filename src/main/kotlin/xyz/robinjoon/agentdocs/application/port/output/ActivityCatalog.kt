package xyz.robinjoon.agentdocs.application.port.output

import xyz.robinjoon.agentdocs.application.model.ActivityEntry
import xyz.robinjoon.agentdocs.application.model.ActivityQuery

interface ActivityCatalog {
    fun find(query: ActivityQuery): List<ActivityEntry>
}
