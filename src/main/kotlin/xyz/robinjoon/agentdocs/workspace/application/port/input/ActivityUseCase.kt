package xyz.robinjoon.agentdocs.workspace.application.port.input

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
    ): List<ActivityView>

    // Entries after since in the caller's visible spaces, oldest first.
    fun changesSince(
        access: Access,
        since: Instant,
        limit: Int,
    ): ChangeFeed
}
