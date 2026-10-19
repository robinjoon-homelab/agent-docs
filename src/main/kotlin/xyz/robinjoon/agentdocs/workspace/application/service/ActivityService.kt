package xyz.robinjoon.agentdocs.workspace.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.application.model.ActivityEntry
import xyz.robinjoon.agentdocs.application.model.ActivityQuery
import xyz.robinjoon.agentdocs.application.port.output.ActivityCatalog
import xyz.robinjoon.agentdocs.workspace.application.model.ActivityView
import xyz.robinjoon.agentdocs.workspace.application.model.ChangeFeed
import xyz.robinjoon.agentdocs.workspace.application.port.input.ActivityUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.SpaceNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceRepository
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.SpaceScope
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import java.time.Instant

@Transactional(readOnly = true)
class ActivityService(
    private val catalog: ActivityCatalog,
    private val spaces: SpaceRepository,
    private val documents: DocumentRepository,
) : ActivityUseCase {
    override fun recent(
        access: Access,
        space: SpaceSlug?,
        limit: Int,
    ): List<ActivityView> {
        val bounded = limit.coerceIn(1, MAX_LIMIT)
        if (space == null) {
            Permissions.requireOwner(access, "reading the whole activity log")
            return viewsOf(catalog.find(ActivityQuery(null, null, null, bounded, oldestFirst = false)))
        }
        val target = spaces.findBySlug(space)?.takeIf { access.sees(it.id) } ?: throw SpaceNotFoundException(space)
        return viewsOf(catalog.find(ActivityQuery(setOf(target.id.value), null, null, bounded, oldestFirst = false)))
    }

    // The owner's feed also includes entries outside spaces; an agent sees only its listed spaces.
    override fun changesSince(
        access: Access,
        since: Instant,
        limit: Int,
    ): ChangeFeed {
        val listed = (access.spaces as? SpaceScope.Listed)?.spaces?.map { it.value }?.toSet()
        val entries = catalog.find(ActivityQuery(listed, since, null, limit.coerceIn(1, MAX_LIMIT), oldestFirst = true))
        val visible =
            entries.filter { entry ->
                entry.record.subject.spaceId
                    ?.let { access.sees(SpaceId(it)) } ?: access.isOwner
            }
        return ChangeFeed(viewsOf(visible), entries.lastOrNull()?.record?.at ?: since)
    }

    private fun viewsOf(entries: List<ActivityEntry>): List<ActivityView> {
        val spaceIds = entries.mapNotNull { it.record.subject.spaceId }.map(::SpaceId).toSet()
        val spacesById = spaces.findByIds(spaceIds).associateBy { it.id.value }
        val documentIds = entries.mapNotNull { it.record.subject.documentId }.distinct().map(::DocumentId)
        val titles = documents.findByIds(documentIds).associate { it.id.value to it.header.title }
        return entries.map { entry ->
            val subject = entry.record.subject
            ActivityView(entry, subject.spaceId?.let(spacesById::get), subject.documentId?.let(titles::get))
        }
    }

    private companion object {
        const val MAX_LIMIT = 200
    }
}
