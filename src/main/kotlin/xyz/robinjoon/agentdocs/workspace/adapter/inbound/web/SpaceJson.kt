package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import xyz.robinjoon.agentdocs.workspace.application.model.SpaceGuide
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceOverview
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceView
import xyz.robinjoon.agentdocs.workspace.domain.space.Space

// Space responses with what the space points agents to. agentGuide is set only while the guide is a document of the
// space outside the trash; otherwise it is null and agentGuideIssue says why, without naming the document.
object SpaceJson {
    fun view(view: SpaceView): Map<String, Any?> = space(view.space, view.guide)

    fun overview(overview: SpaceOverview): Map<String, Any?> =
        space(overview.space, overview.guide) +
            mapOf("documentCount" to overview.documentCount, "lastActivityAt" to overview.lastActivityAt)

    fun space(
        space: Space,
        guide: SpaceGuide?,
    ): Map<String, Any?> =
        WorkspaceJson.space(space) +
            mapOf(
                "defaultRepository" to space.guidance.defaultRepository,
                "agentGuide" to (guide as? SpaceGuide.Offered)?.let(::guideOf),
                "agentGuideIssue" to (guide as? SpaceGuide.Withheld)?.issue?.name?.lowercase(),
            )

    fun guideOf(guide: SpaceGuide.Offered): Map<String, Any> =
        mapOf("documentId" to guide.documentId.value, "title" to guide.title, "path" to guide.path)
}
