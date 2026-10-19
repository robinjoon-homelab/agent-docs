package xyz.robinjoon.agentdocs.workspace.application.port.output

import xyz.robinjoon.agentdocs.workspace.application.model.CommentMatch
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId

// Substring search, case-insensitive, over documents outside the trash, newest first. It returns ids only; the
// documents come from the document repository.
interface SearchCatalog {
    // Documents whose title or body contains the term.
    fun documents(
        spaces: Set<SpaceId>,
        term: String,
        limit: Int,
    ): List<DocumentId>

    fun titles(
        spaces: Set<SpaceId>,
        term: String,
        limit: Int,
    ): List<DocumentId>

    fun comments(
        spaces: Set<SpaceId>,
        term: String,
        limit: Int,
    ): List<CommentMatch>
}
