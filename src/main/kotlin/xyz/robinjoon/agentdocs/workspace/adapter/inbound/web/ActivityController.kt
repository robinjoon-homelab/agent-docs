package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import xyz.robinjoon.agentdocs.workspace.application.port.input.ActivityUseCase
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import java.security.Principal
import java.time.Instant

@RestController
class ActivityController(
    private val activity: ActivityUseCase,
) {
    @GetMapping("/api/activity")
    fun recent(
        @RequestParam(required = false) space: String?,
        @RequestParam(defaultValue = "50") limit: Int,
        principal: Principal?,
    ): List<Map<String, Any?>> = activity.recent(CallerAccess.of(principal), space?.let(::SpaceSlug), limit).map(WorkspaceJson::activity)

    // Use initialSince as since with nextCursor to preserve equal timestamps; nextSince is legacy timestamp continuation.
    @GetMapping("/api/changes")
    fun changes(
        @RequestParam since: Instant,
        @RequestParam(defaultValue = "100") limit: Int,
        @RequestParam(required = false) cursor: Long?,
        principal: Principal?,
    ): Map<String, Any?> {
        val feed = activity.changesSince(CallerAccess.of(principal), since, limit, cursor)
        return mapOf(
            "entries" to feed.entries.map(WorkspaceJson::activity),
            "nextSince" to feed.nextSince,
            "nextCursor" to feed.nextCursor,
            "initialSince" to feed.initialSince,
        )
    }
}
