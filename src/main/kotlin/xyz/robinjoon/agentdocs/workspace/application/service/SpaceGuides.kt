package xyz.robinjoon.agentdocs.workspace.application.service

import xyz.robinjoon.agentdocs.workspace.application.model.GuideCandidate
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceGuide
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceGuideIssue
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceGuideCatalog
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.space.Space

// Decides how the guide a space names reaches readers. The space keeps only the document id, so trashing, deleting,
// or moving that document leaves the setting in place; reading it then withholds the guide instead of offering a
// document that is no longer the space's.
internal class SpaceGuides(
    private val catalog: SpaceGuideCatalog,
) {
    fun of(space: Space): SpaceGuide? = of(listOf(space))[space.id]

    // One read for every guide of the spaces; spaces that name no guide are absent.
    fun of(spaces: List<Space>): Map<SpaceId, SpaceGuide> {
        val named = spaces.mapNotNull { space -> space.guidance.guideDocumentId?.let { space to DocumentId(it) } }
        if (named.isEmpty()) return emptyMap()
        val found = catalog.find(named.map { it.second }.toSet()).associateBy { it.id }
        return named.associate { (space, id) -> space.id to guideOf(space, found[id]) }
    }

    // A guide can be set only to a document of the same space outside the trash. Every other case gets the same
    // answer, so the request cannot tell a document of another space from a missing one.
    fun requireSettable(
        space: Space,
        document: DocumentId,
    ) {
        val candidate = catalog.find(setOf(document)).singleOrNull()
        if (candidate == null || candidate.spaceId != space.id || candidate.trashed) {
            throw InvalidRequestException(
                "the agent guide must be a document of this space outside the trash",
                "pick a document that is listed in this space",
            )
        }
    }

    private fun guideOf(
        space: Space,
        candidate: GuideCandidate?,
    ): SpaceGuide =
        when {
            candidate == null || candidate.spaceId != space.id -> SpaceGuide.Withheld(SpaceGuideIssue.UNAVAILABLE)
            candidate.trashed -> SpaceGuide.Withheld(SpaceGuideIssue.TRASHED)
            else -> SpaceGuide.Offered(candidate.id, candidate.title, candidate.path)
        }
}
