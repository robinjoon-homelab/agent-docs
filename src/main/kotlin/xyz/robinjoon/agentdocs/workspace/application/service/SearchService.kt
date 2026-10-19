package xyz.robinjoon.agentdocs.workspace.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentRef
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.SearchHit
import xyz.robinjoon.agentdocs.workspace.application.model.SearchHitKind
import xyz.robinjoon.agentdocs.workspace.application.port.input.SearchUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.SpaceNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.SearchCatalog
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceRepository
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.space.Space
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug

// Plain substring search. Korean has no word boundaries a simple index would get right, so matching any part of the
// text is the expected behavior for now.
@Transactional(readOnly = true)
class SearchService(
    private val catalog: SearchCatalog,
    private val documents: DocumentRepository,
    private val spaces: SpaceRepository,
) : SearchUseCase {
    override fun search(
        access: Access,
        term: String,
        space: SpaceSlug?,
    ): List<SearchHit> {
        val needle = termOf(term)
        val scope = scopeOf(access, space).associateBy { it.id }
        if (scope.isEmpty()) return emptyList()
        val found = documents.findByIds(catalog.documents(scope.keys, needle, LIMIT))
        val documentHits = found.flatMap { document -> scope[document.spaceId]?.let { hitsIn(document, it, needle) }.orEmpty() }
        val comments = catalog.comments(scope.keys, needle, LIMIT)
        val commented = documents.findByIds(comments.map { it.documentId }.distinct()).associateBy { it.id }
        val commentHits =
            comments.mapNotNull { match ->
                val document = commented[match.documentId] ?: return@mapNotNull null
                val home = scope[document.spaceId] ?: return@mapNotNull null
                SearchHit(SearchHitKind.COMMENT, home, refOf(document), null, match.threadId, snippet(match.body, needle))
            }
        return (documentHits + commentHits).take(LIMIT)
    }

    override fun titles(
        access: Access,
        term: String,
    ): List<Pair<Space, DocumentRef>> {
        val scope = scopeOf(access, null).associateBy { it.id }
        if (scope.isEmpty()) return emptyList()
        return documents.findByIds(catalog.titles(scope.keys, termOf(term), TITLE_LIMIT)).mapNotNull { document ->
            scope[document.spaceId]?.let { it to refOf(document) }
        }
    }

    private fun hitsIn(
        document: Document,
        space: Space,
        needle: String,
    ): List<SearchHit> {
        val title =
            document.header.title.takeIf { it.contains(needle, ignoreCase = true) }?.let {
                SearchHit(SearchHitKind.TITLE, space, refOf(document), null, null, it)
            }
        val blocks =
            document.blocks
                .filter { it.plainText().contains(needle, ignoreCase = true) }
                .take(BLOCKS_PER_DOCUMENT)
                .map { SearchHit(SearchHitKind.BLOCK, space, refOf(document), it.id, null, snippet(it.plainText(), needle)) }
        return listOfNotNull(title) + blocks
    }

    private fun scopeOf(
        access: Access,
        slug: SpaceSlug?,
    ): List<Space> {
        // Searching every space leaves archived ones out; an archived space is still searched when asked for by name.
        if (slug == null) return spaces.findAll().filter { access.sees(it.id) && !it.isArchived }
        return listOf(spaces.findBySlug(slug)?.takeIf { access.sees(it.id) } ?: throw SpaceNotFoundException(slug))
    }

    private fun termOf(term: String): String {
        val needle = term.trim()
        if (needle.length !in 1..MAX_TERM) throw InvalidRequestException("search for 1 to $MAX_TERM characters")
        return needle
    }

    // Up to 40 characters on each side of the first match, on one line.
    private fun snippet(
        text: String,
        needle: String,
    ): String {
        val flat = text.replace(Regex("\\s+"), " ")
        val at = flat.indexOf(needle, ignoreCase = true).coerceAtLeast(0)
        val start = (at - SNIPPET_CONTEXT).coerceAtLeast(0)
        val end = (at + needle.length + SNIPPET_CONTEXT).coerceAtMost(flat.length)
        return (if (start > 0) "…" else "") + flat.substring(start, end) + (if (end < flat.length) "…" else "")
    }

    private fun refOf(document: Document): DocumentRef = DocumentRef(document.id, document.header.path, document.header.title)

    private companion object {
        const val LIMIT = 50
        const val TITLE_LIMIT = 20
        const val BLOCKS_PER_DOCUMENT = 3
        const val MAX_TERM = 100
        const val SNIPPET_CONTEXT = 40
    }
}
