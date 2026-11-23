package xyz.robinjoon.agentdocs.workspace.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentFilter
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentListPosition
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentListing
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentListingPage
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
import xyz.robinjoon.agentdocs.workspace.application.port.output.MarkdownTokenCounter
import xyz.robinjoon.agentdocs.workspace.application.port.output.RevisionCatalog
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.ThreadRepository
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.history.Revision
import xyz.robinjoon.agentdocs.workspace.domain.document.history.RevisionDiff
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug

@Transactional(readOnly = true)
class DocumentQueryService(
    private val documents: DocumentRepository,
    private val revisions: RevisionCatalog,
    private val spaces: SpaceRepository,
    private val threads: ThreadRepository,
    tokens: MarkdownTokenCounter,
    publicUrl: String? = null,
) : DocumentQueryUseCase {
    private val markdownPages = MarkdownPages(tokens, publicUrl)

    override fun open(
        access: Access,
        id: DocumentId,
    ): DocumentView {
        val document = readable(access, id)
        val space = spaces.findById(document.spaceId) ?: throw DocumentNotFoundException(id)
        return DocumentView(document, space, revisions.marks(id))
    }

    override fun list(
        access: Access,
        space: SpaceSlug,
        filter: DocumentFilter,
    ): DocumentListingPage {
        val target = spaces.findBySlug(space)?.takeIf { access.sees(it.id) } ?: throw SpaceNotFoundException(space)
        val found = documents.list(setOf(target.id), filter, PAGE_SIZE + 1)
        val visible = found.take(PAGE_SIZE)
        val last = visible.lastOrNull()?.takeIf { found.size > PAGE_SIZE }
        return DocumentListingPage(visible, last?.let { DocumentListPosition(it.lifecycle.updatedAt, it.id.value).toString() })
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
        val source = request.revision?.let { markdownRevision(document, it) } ?: document
        return markdownPages.chunk(source, space.slug, request)
    }

    private fun markdownRevision(
        document: Document,
        number: Int,
    ): Document {
        val revision = revisions.find(document.id, number) ?: throw RevisionNotFoundException("${document.id} rev $number")
        val header = revision.content.header.copy(spaceId = document.spaceId)
        val lifecycle = document.lifecycle.copy(revision = revision.number, version = revision.version)
        return Document.restore(document.id, header, revision.content.blocks, lifecycle)
    }

    private fun readable(
        access: Access,
        id: DocumentId,
    ): Document = documents.findById(id)?.takeIf { access.sees(it.spaceId) } ?: throw DocumentNotFoundException(id)

    private companion object {
        const val LIST_LIMIT = 1_000
        const val PAGE_SIZE = 200
    }
}
