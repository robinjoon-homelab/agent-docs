package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import xyz.robinjoon.agentdocs.workspace.application.port.input.ActivityUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.UsageUseCase
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import java.security.Principal
import java.time.Instant

@RestController
class ActivityController(
    private val activity: ActivityUseCase,
    private val usage: UsageUseCase,
) {
    @GetMapping("/api/activity")
    fun recent(
        @RequestParam(required = false) space: String?,
        @RequestParam(defaultValue = "50") limit: Int,
        principal: Principal?,
    ): List<Map<String, Any?>> = activity.recent(CallerAccess.of(principal), space?.let(::SpaceSlug), limit).map(WorkspaceJson::activity)

    // Continue with nextSince to read on from where this page ended.
    @GetMapping("/api/changes")
    fun changes(
        @RequestParam since: Instant,
        @RequestParam(defaultValue = "100") limit: Int,
        principal: Principal?,
    ): Map<String, Any?> {
        val feed = activity.changesSince(CallerAccess.of(principal), since, limit)
        return mapOf("entries" to feed.entries.map(WorkspaceJson::activity), "nextSince" to feed.nextSince)
    }

    @GetMapping("/api/metrics/usage")
    fun usage(principal: Principal?): Map<String, Any?> {
        val report = usage.report(CallerAccess.of(principal))
        val names = report.spaces.associate { it.id to it }
        val weeks =
            report.weeks.groupBy { it.weekStart }.map { (week, rows) ->
                val spaces =
                    rows.mapNotNull { row ->
                        names[row.spaceId]?.let {
                            mapOf(
                                "slug" to it.slug.value,
                                "name" to it.profile.name,
                                "documents" to row.documents,
                                "comments" to row.comments,
                            )
                        }
                    }
                mapOf("weekStart" to week, "spaces" to spaces)
            }
        return mapOf(
            "weeks" to weeks,
            "agentResponseMinutesMedian" to report.agentResponseMedian?.toMinutes(),
            "revisitedDocuments" to report.revisitedDocuments,
        )
    }
}
