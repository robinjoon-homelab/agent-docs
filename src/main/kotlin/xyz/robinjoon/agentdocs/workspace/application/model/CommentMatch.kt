package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId

// A comment whose body contains the search term, as the catalog finds it.
data class CommentMatch(
    val documentId: DocumentId,
    val threadId: ThreadId,
    val body: String,
)
