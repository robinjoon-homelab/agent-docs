package xyz.robinjoon.agentdocs.workspace.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentFilter
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentListing
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentView
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownChunk
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownRequest
import xyz.robinjoon.agentdocs.workspace.application.model.RevisionEntry
import xyz.robinjoon.agentdocs.workspace.application.model.RevisionFilter
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentQueryUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.RevisionNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.SpaceNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentViewReporter
import xyz.robinjoon.agentdocs.workspace.application.port.output.RevisionCatalog
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.ThreadRepository
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.history.Revision
import xyz.robinjoon.agentdocs.workspace.domain.document.history.RevisionDiff
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

@Transactional(readOnly = true)
class DocumentQueryService(
    private val documents: DocumentRepository,
    private val revisions: RevisionCatalog,
    private val spaces: SpaceRepository,
    private val threads: ThreadRepository,
    private val views: DocumentViewReporter,
    private val clock: Clock,
) : DocumentQueryUseCase {
    @Transactional
    override fun open(
        access: Access,
        id: DocumentId,
    ): DocumentView {
        val document = readable(access, id)
        val space = spaces.findById(document.spaceId) ?: throw DocumentNotFoundException(id)
        if (!access.actor.isAgent) {
            views.record(id, access.actor.login, LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC))
        }
        return DocumentView(document, space, revisions.marks(id))
    }

    override fun list(
        access: Access,
        space: SpaceSlug,
        filter: DocumentFilter,
    ): List<DocumentListing> {
        val target = spaces.findBySlug(space)?.takeIf { access.sees(it.id) } ?: throw SpaceNotFoundException(space)
        return documents.list(setOf(target.id), filter, LIST_LIMIT)
    }

    override fun recent(
        access: Access,
        limit: Int,
    ): List<DocumentListing> {
        val visible =
            spaces
                .findAll()
                .filter { access.sees(it.id) }
                .map { it.id }
                .toSet()
        return if (visible.isEmpty()) emptyList() else documents.list(visible, DocumentFilter.active, limit.coerceIn(1, LIST_LIMIT))
    }

    override fun revisions(
        access: Access,
        id: DocumentId,
        filter: RevisionFilter,
    ): List<RevisionEntry> {
        readable(access, id)
        val links = threads.linksByRevision(id)
        return revisions.list(id, filter).map { RevisionEntry(it, links[it.number].orEmpty()) }
    }

    override fun revision(
        access: Access,
        id: DocumentId,
        number: Int,
    ): Revision {
        readable(access, id)
        return revisions.find(id, number) ?: throw RevisionNotFoundException("$id rev $number")
    }

    override fun diff(
        access: Access,
        id: DocumentId,
        from: Int,
        to: Int?,
    ): RevisionDiff {
        val document = readable(access, id)
        val before = revisions.find(id, from) ?: throw RevisionNotFoundException("$id rev $from")
        val after = to?.let { revisions.find(id, it)?.content?.blocks ?: throw RevisionNotFoundException("$id rev $it") } ?: document.blocks
        return RevisionDiff.between(from, before.content.blocks, to ?: document.revision, after)
    }

    override fun markdown(
        access: Access,
        id: DocumentId,
        request: MarkdownRequest,
    ): MarkdownChunk {
        val document = readable(access, id)
        val space = spaces.findById(document.spaceId) ?: throw DocumentNotFoundException(id)
        return MarkdownPages.chunk(document, space.slug, request)
    }

    private fun readable(
        access: Access,
        id: DocumentId,
    ): Document = documents.findById(id)?.takeIf { access.sees(it.spaceId) } ?: throw DocumentNotFoundException(id)

    private companion object {
        const val LIST_LIMIT = 1_000
    }
}
