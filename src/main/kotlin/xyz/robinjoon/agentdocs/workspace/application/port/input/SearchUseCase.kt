package xyz.robinjoon.agentdocs.workspace.application.port.input

import xyz.robinjoon.agentdocs.workspace.application.model.DocumentRef
import xyz.robinjoon.agentdocs.workspace.application.model.SearchHit
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.space.Space
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug

interface SearchUseCase {
    // Without an explicit scope the caller may search at most one visible space.
    fun search(
        access: Access,
        term: String,
        space: SpaceSlug?,
        allSpaces: Boolean = false,
    ): List<SearchHit>

    // Title matches across every visible space, for jumping to a document.
    fun titles(
        access: Access,
        term: String,
    ): List<Pair<Space, DocumentRef>>
}
