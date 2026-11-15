package xyz.robinjoon.agentdocs.workspace.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.workspace.application.model.MobileMemoTarget
import xyz.robinjoon.agentdocs.workspace.application.model.NavigationDocument
import xyz.robinjoon.agentdocs.workspace.application.model.NavigationPage
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentNotFoundException
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
import java.time.Clock

@Transactional(readOnly = true)
class NavigationService(
    private val personal: PersonalDocumentStore,
    private val catalog: NavigationCatalog,
    private val documents: DocumentRepository,
    private val spaces: SpaceRepository,
    private val clock: Clock,
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
        access: Access,
        id: DocumentId,
    ) {
        readable(access, id)
        personal.recordView(access.actor.userId, id, clock.instant())
    }

    @Transactional
    override fun favorite(
        access: Access,
        id: DocumentId,
        enabled: Boolean,
    ) {
        readable(access, id)
        personal.favorite(access.actor.userId, id, clock.instant().takeIf { enabled })
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
    ) {
        documents.findById(id)?.takeIf { !it.isTrashed && access.sees(it.spaceId) }
            ?: throw DocumentNotFoundException(id)
    }

    private fun page(
        entries: List<NavigationDocument>,
        limit: Int,
    ): NavigationPage = NavigationPage(entries.take(limit.coerceIn(1, 1000)), entries.size)
}
