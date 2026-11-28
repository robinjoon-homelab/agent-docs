package xyz.robinjoon.agentdocs.workspace.application.port.input

import xyz.robinjoon.agentdocs.application.model.ActivityFilter
import xyz.robinjoon.agentdocs.workspace.application.model.ActivityView
import xyz.robinjoon.agentdocs.workspace.application.model.ChangeFeed
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import java.time.Instant

interface ActivityUseCase {
    // With a space, the activity of that space for anyone who may read it. Without one, the whole log, owner only.
    fun recent(
        access: Access,
        space: SpaceSlug?,
        limit: Int,
        filter: ActivityFilter = ActivityFilter(),
    ): List<ActivityView>

    // Entries after the initial since and cursor ID in visible spaces, in insertion order. Keep since unchanged.
    fun changesSince(
        access: Access,
        since: Instant,
        limit: Int,
        cursor: Long? = null,
    ): ChangeFeed
}
