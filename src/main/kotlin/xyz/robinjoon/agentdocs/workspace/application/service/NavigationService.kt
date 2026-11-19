package xyz.robinjoon.agentdocs.workspace.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.workspace.application.model.MobileMemoTarget
import xyz.robinjoon.agentdocs.workspace.application.model.NavigationDocument
import xyz.robinjoon.agentdocs.workspace.application.model.NavigationPage
import xyz.robinjoon.agentdocs.workspace.application.model.OperationRecord
import xyz.robinjoon.agentdocs.workspace.application.model.OperationResultState
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.LegacyOperationException
import xyz.robinjoon.agentdocs.workspace.application.port.input.NavigationUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.SpaceNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.NavigationCatalog
import xyz.robinjoon.agentdocs.workspace.application.port.output.PersonalDocumentStore
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceRepository
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug

@Transactional(readOnly = true)
class NavigationService(
    private val personal: PersonalDocumentStore,
    private val catalog: NavigationCatalog,
    private val documents: DocumentRepository,
    private val spaces: SpaceRepository,
    private val journal: WriteJournal,
) : NavigationUseCase {
    override fun mobileTarget(
        access: Access,
        space: String,
        path: String,
    ): MobileMemoTarget? {
        val slug = SpaceSlug(space)
        val target = spaces.findBySlug(slug)?.takeIf { access.sees(it.id) } ?: throw SpaceNotFoundException(slug)
        val document = documents.findByPath(target.id, DocumentPath(path))?.takeUnless { it.isTrashed } ?: return null
        return MobileMemoTarget(document.id, document.version, document.blocks)
    }

    override fun isFavorite(
        access: Access,
        id: DocumentId,
    ): Boolean {
        readable(access, id)
        return personal.list(access.actor.userId).any { it.documentId == id && it.favoriteAt != null }
    }

    override fun recent(
        access: Access,
        limit: Int,
    ): NavigationPage {
        val ids =
            personal
                .list(access.actor.userId)
                .filter { it.viewedAt != null }
                .sortedByDescending { it.viewedAt }
                .map { it.documentId }
        return personalPage(access, ids, limit)
    }

    override fun favorites(
        access: Access,
        limit: Int,
    ): NavigationPage {
        val ids =
            personal
                .list(access.actor.userId)
                .filter { it.favoriteAt != null }
                .sortedByDescending { it.favoriteAt }
                .map { it.documentId }
        return personalPage(access, ids, limit)
    }

    @Transactional
    override fun recordView(
        context: WriteContext,
        id: DocumentId,
    ) {
        write(context, id, "navigation.view") {
            personal.recordView(context.access.actor.userId, id, journal.now())
        }
    }

    @Transactional
    override fun favorite(
        context: WriteContext,
        id: DocumentId,
        enabled: Boolean,
    ) {
        write(context, id, if (enabled) "navigation.favorite" else "navigation.unfavorite") {
            personal.favorite(context.access.actor.userId, id, journal.now().takeIf { enabled })
        }
    }

    private fun write(
        context: WriteContext,
        id: DocumentId,
        kind: String,
        change: () -> Unit,
    ) {
        val previous = journal.replayed(context, kind)
        if (previous != null) {
            return replay(context.access, id, previous)
        }
        // A concurrent purge must see a keyed write's completion marker to retain its last space.
        readable(context.access, id, forUpdate = context.operationId != null)
        change()
        journal.remember(context, OperationRecord(kind, id.value, null, state = OperationResultState.COMPLETED))
    }

    private fun replay(
        access: Access,
        requested: DocumentId,
        record: OperationRecord,
    ) {
        // A completed navigation write has no body to discard. Its current or last space still gates the acknowledgment.
        if (!access.isOwner) {
            val space = documents.findById(DocumentId(record.target))?.spaceId ?: record.purgedFrom
            if (space == null || !access.sees(space)) throw DocumentNotFoundException(requested)
        }
        if (record.state != OperationResultState.COMPLETED) throw LegacyOperationException(record)
    }

    override fun backlinks(
        access: Access,
        id: DocumentId,
        limit: Int,
    ): NavigationPage {
        readable(access, id)
        return page(catalog.linkingTo(id, visibleSpaces(access)), limit)
    }

    private fun personalPage(
        access: Access,
        ids: List<DocumentId>,
        limit: Int,
    ): NavigationPage {
        val visible = catalog.documents(visibleSpaces(access)).associateBy { it.id }
        return page(ids.mapNotNull { visible[it] }, limit)
    }

    private fun visibleSpaces(access: Access): Set<SpaceId> =
        spaces
            .findAll()
            .filter { access.sees(it.id) }
            .map { it.id }
            .toSet()

    private fun readable(
        access: Access,
        id: DocumentId,
        forUpdate: Boolean = false,
    ) {
        val document = if (forUpdate) documents.findByIdForUpdate(id) else documents.findById(id)
        if (document == null || document.isTrashed || !access.sees(document.spaceId)) throw DocumentNotFoundException(id)
    }

    private fun page(
        entries: List<NavigationDocument>,
        limit: Int,
    ): NavigationPage = NavigationPage(entries.take(limit.coerceIn(1, 1000)), entries.size)
}
