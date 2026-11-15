package xyz.robinjoon.agentdocs.application.port.output

import xyz.robinjoon.agentdocs.application.model.ActivityEntry
import xyz.robinjoon.agentdocs.application.model.ActivityQuery
import xyz.robinjoon.agentdocs.application.model.ActivityScan

interface ActivityCatalog {
    fun find(query: ActivityQuery): List<ActivityEntry>

    // Entries at or after scan.since, oldest id first.
    fun scan(scan: ActivityScan): List<ActivityEntry>
}
