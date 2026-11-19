package xyz.robinjoon.agentdocs.workspace.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.application.model.ActivitySubject
import xyz.robinjoon.agentdocs.application.model.CreatedComment
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.OperationRecord
import xyz.robinjoon.agentdocs.workspace.application.model.OperationResultState
import xyz.robinjoon.agentdocs.workspace.application.model.SuggestionRequest
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadView
import xyz.robinjoon.agentdocs.workspace.application.model.WorkspaceActivity
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.input.CommentNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.LegacyOperationException
import xyz.robinjoon.agentdocs.workspace.application.port.input.PermissionDeniedException
import xyz.robinjoon.agentdocs.workspace.application.port.input.ResultRemovedException
import xyz.robinjoon.agentdocs.workspace.application.port.input.SuggestionUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.ThreadNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.ThreadRepository
import xyz.robinjoon.agentdocs.workspace.domain.Permission
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.history.ChangeOrigin
import xyz.robinjoon.agentdocs.workspace.domain.document.history.Revision
import xyz.robinjoon.agentdocs.workspace.domain.document.history.RevisionChange
import xyz.robinjoon.agentdocs.workspace.domain.thread.AnchorResolver
import xyz.robinjoon.agentdocs.workspace.domain.thread.Comment
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentThread
import xyz.robinjoon.agentdocs.workspace.domain.thread.Suggestion
import xyz.robinjoon.agentdocs.workspace.domain.thread.SuggestionBasis
import xyz.robinjoon.agentdocs.workspace.domain.thread.SuggestionDecision
import xyz.robinjoon.agentdocs.workspace.domain.thread.SuggestionPatch
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId
import java.util.UUID

@Transactional(readOnly = true)
class SuggestionService(
    private val threads: ThreadRepository,
    private val documents: DocumentRepository,
    private val journal: WriteJournal,
    private val inbox: xyz.robinjoon.agentdocs.workspace.application.port.output.InboxRepository,
) : SuggestionUseCase {
    @Transactional
    override fun suggest(
        context: WriteContext,
        thread: ThreadId,
        request: SuggestionRequest,
    ): ThreadView {
        journal.replayed(context, SUGGEST)?.let { return replay(context, it, Permission.COMMENT) }
        val (current, document) = locked(context, thread, Permission.COMMENT)
        current.requireAvailable(context.access.actor, journal.now())
        val anchor = AnchorPlacement.place(request.anchor, document)
        val block = document.block(SuggestionPatch.blockId(anchor)) ?: throw InvalidRequestException("block not found")
        val hash =
            request.hash ?: xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown
                .hash(block)
        val basis = SuggestionBasis(anchor, hash, request.before, request.after)
        SuggestionPatch.apply(document.blocks, basis)
        val at = journal.now()
        val comment = Comment.write(CommentId(UUID.randomUUID()), context.access.actor, "수정 제안", at)
        val replied = current.reply(comment, at)
        val next = replied.collaborate(replied.collaboration.suggested(Suggestion(comment.id, basis)), at)
        val confirmed = PendingEdits(documents, journal).confirm(document)
        val saved = threads.update(next)
        notify(saved, comment, document)
        val event = activity(SUGGEST, saved, confirmed.revision, createdComment(saved, comment, document))
        return remember(context, view(saved, document).copy(written = comment.id), event)
    }

    @Transactional
    override fun decide(
        context: WriteContext,
        comment: CommentId,
        accept: Boolean,
    ): ThreadView {
        requireHuman(context)
        journal.replayed(context, DECIDE)?.let { return replay(context, it, Permission.EDIT) }
        val known = threads.findByComment(comment) ?: throw CommentNotFoundException(comment)
        val (current, document) = locked(context, known.id, Permission.EDIT)
        val suggestion =
            current.collaboration.suggestions.firstOrNull { it.comment == comment }
                ?: throw InvalidRequestException("comment has no suggestion")
        if (suggestion.decision != null) throw InvalidRequestException("suggestion already decided")
        val changed = if (accept) apply(context, document, suggestion) else document
        val now = journal.now()
        val decision = SuggestionDecision(context.access.actor, now, accept, changed.revision.takeIf { accept })
        var next = current.collaborate(current.collaboration.decided(comment, decision), now)
        if (accept) next = next.linkRevision(changed.revision, now) ?: next
        val saved = threads.update(next)
        return remember(context, view(saved, changed), activity(DECIDE, saved, changed.revision))
    }

    private fun createdComment(
        thread: CommentThread,
        comment: Comment,
        document: Document,
    ): CreatedComment =
        CreatedComment(
            comment.id.value,
            InboxRecipients.of(thread, comment, document.header.properties.assignee)
                [xyz.robinjoon.agentdocs.workspace.domain.inbox.InboxKind.MENTION].orEmpty(),
        )

    private fun notify(
        thread: CommentThread,
        comment: Comment,
        document: Document,
    ) {
        val target =
            xyz.robinjoon.agentdocs.workspace.domain.inbox
                .NoticeTarget(document.spaceId, document.id, thread.id, comment.id)
        val items =
            InboxRecipients.of(thread, comment, document.header.properties.assignee).flatMap { (kind, logins) ->
                logins.map {
                    xyz.robinjoon.agentdocs.workspace.domain.inbox.InboxItem.deliver(
                        xyz.robinjoon.agentdocs.workspace.domain.inbox
                            .InboxItemId(UUID.randomUUID()),
                        it,
                        xyz.robinjoon.agentdocs.workspace.domain.inbox.Notice
                            .of(kind, target, comment.author, comment.body),
                        journal.now(),
                    )
                }
            }
        if (items.isNotEmpty()) inbox.addAll(items)
    }

    private fun apply(
        context: WriteContext,
        document: Document,
        suggestion: Suggestion,
    ): Document {
        if (document.isTrashed) throw InvalidRequestException("cannot accept a suggestion in the trash")
        val blocks = SuggestionPatch.apply(document.blocks, suggestion.basis)
        val current = PendingEdits(documents, journal).confirm(document)
        val at = journal.now()
        val next = current.revise(current.header, blocks, at)
        val change =
            RevisionChange.of(
                ChangeOrigin(context.access.actor, context.source),
                "수정 제안 수락",
                listOf(SuggestionPatch.blockId(suggestion.basis.anchor)),
                at,
            )
        val saved = documents.update(next, Revision.of(next, change), false)
        journal.confirmed(saved, change)
        journal.record(context, "document.revised", ActivitySubject(saved.spaceId.value, saved.id.value, null, saved.revision), "수정 제안 수락")
        return saved
    }

    // Suggesting needs comment permission in the thread's space; deciding needs edit permission there, which the owner
    // and editors hold.
    private fun locked(
        context: WriteContext,
        id: ThreadId,
        needed: Permission,
    ): Pair<CommentThread, Document> {
        val known = threads.findById(id)?.takeIf { context.access.sees(it.location.spaceId) } ?: throw ThreadNotFoundException(id)
        val document =
            documents
                .findByIdForUpdate(known.location.documentId)
                ?.takeIf { context.access.sees(it.spaceId) } ?: throw ThreadNotFoundException(id)
        val action = if (needed == Permission.EDIT) "deciding suggestions" else "suggesting changes"
        Permissions.require(context.access, document.spaceId, needed, action)
        val current = threads.findByIdForUpdate(id) ?: throw ThreadNotFoundException(id)
        return current to document
    }

    // Agents never decide, even with edit permission: a person accepts or rejects what is proposed.
    private fun requireHuman(context: WriteContext) {
        if (context.access.actor.isAgent) {
            throw PermissionDeniedException(Permission.EDIT, "deciding suggestions, which only a person does,")
        }
    }

    // A retried request passes the same check as the first attempt before it gets the first answer back.
    private fun replay(
        context: WriteContext,
        record: OperationRecord,
        needed: Permission,
    ): ThreadView {
        val known = threads.findById(ThreadId(record.target)) ?: rejectMissingThreadReplay(context.access, record, needed)
        locked(context, known.id, needed)
        if (record.state == OperationResultState.REMOVED) throw ResultRemovedException(record)
        return record.threadView ?: throw LegacyOperationException(record)
    }

    private fun remember(
        context: WriteContext,
        view: ThreadView,
        event: WorkspaceActivity,
    ): ThreadView {
        journal.remember(context, OperationRecord(event.kind, view.thread.id.value, null, threadView = view))
        journal.record(context, event)
        return view
    }

    private fun activity(
        kind: String,
        thread: CommentThread,
        revision: Int,
        createdComment: CreatedComment? = null,
    ): WorkspaceActivity =
        WorkspaceActivity(
            kind,
            ActivitySubject(thread.location.spaceId.value, thread.location.documentId.value, thread.id.value, revision),
            "수정 제안",
            createdComment,
        )

    private fun view(
        thread: CommentThread,
        document: Document,
    ): ThreadView = ThreadView(thread, AnchorResolver.resolve(thread.location.anchor, document.blocks), null)

    private companion object {
        const val SUGGEST = "thread.suggest"
        const val DECIDE = "thread.suggestion.decide"
    }
}
