package xyz.robinjoon.agentdocs.workspace.application.port.input

import xyz.robinjoon.agentdocs.workspace.application.model.DocumentRef
import xyz.robinjoon.agentdocs.workspace.application.model.SearchHit
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.space.Space
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug

interface SearchUseCase {
    // Titles, blocks, and comments containing the term. space null searches every visible space.
    fun search(
        access: Access,
        term: String,
        space: SpaceSlug?,
    ): List<SearchHit>

    // Title matches across every visible space, for jumping to a document.
    fun titles(
        access: Access,
        term: String,
    ): List<Pair<Space, DocumentRef>>
}
