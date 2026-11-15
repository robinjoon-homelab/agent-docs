package xyz.robinjoon.agentdocs.workspace.application.port.input

import xyz.robinjoon.agentdocs.workspace.application.model.SuggestionRequest
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadView
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId

interface SuggestionUseCase {
    fun suggest(
        context: WriteContext,
        thread: ThreadId,
        request: SuggestionRequest,
    ): ThreadView

    fun decide(
        context: WriteContext,
        comment: CommentId,
        accept: Boolean,
    ): ThreadView
}
