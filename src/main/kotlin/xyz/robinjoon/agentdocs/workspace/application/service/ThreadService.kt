package xyz.robinjoon.agentdocs.workspace.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.application.model.ActivitySubject
import xyz.robinjoon.agentdocs.workspace.application.model.AnchorRequest
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.OperationRecord
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadChange
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadContext
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadCriteria
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadQuery
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadView
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.input.CommentNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.ThreadNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.ThreadUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.InboxRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.RevisionCatalog
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.ThreadRepository
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.Actor
import xyz.robinjoon.agentdocs.workspace.domain.Permission
import xyz.robinjoon.agentdocs.workspace.domain.SpaceScope
import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown
import xyz.robinjoon.agentdocs.workspace.domain.inbox.InboxItem
import xyz.robinjoon.agentdocs.workspace.domain.inbox.InboxItemId
import xyz.robinjoon.agentdocs.workspace.domain.inbox.Notice
import xyz.robinjoon.agentdocs.workspace.domain.inbox.NoticeTarget
import xyz.robinjoon.agentdocs.workspace.domain.space.Space
import xyz.robinjoon.agentdocs.workspace.domain.thread.Anchor
import xyz.robinjoon.agentdocs.workspace.domain.thread.AnchorResolution
import xyz.robinjoon.agentdocs.workspace.domain.thread.AnchorResolver
import xyz.robinjoon.agentdocs.workspace.domain.thread.Comment
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentThread
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadLocation
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadStatus
import java.time.Instant
import java.util.UUID

@Transactional(readOnly = true)
class ThreadService(
    private val threads: ThreadRepository,
    private val documents: DocumentRepository,
    private val spaces: SpaceRepository,
    private val inbox: InboxRepository,
    private val revisions: RevisionCatalog,
    private val journal: WriteJournal,
) : ThreadUseCase {
    @Transactional
    override fun open(
        context: WriteContext,
        document: DocumentId,
        anchor: AnchorRequest,
        body: String,
    ): ThreadView {
        journal.replayed(context, OPEN)?.let { return replay(context.access, it) }
        val target = documents.findById(document)?.takeIf { context.access.sees(it.spaceId) } ?: throw DocumentNotFoundException(document)
        Permissions.require(context.access, target.spaceId, Permission.COMMENT, "commenting")
        val now = journal.now()
        val location = ThreadLocation(target.spaceId, target.id, AnchorPlacement.place(anchor, target), target.revision)
        val comment = Comment.write(CommentId(UUID.randomUUID()), context.access.actor, body, now)
        val thread = threads.add(CommentThread.open(ThreadId(UUID.randomUUID()), location, comment, now))
        notify(thread, comment, target)
        journal.record(context, "thread.opened", subjectOf(thread), "${target.header.title}: ${comment.body.take(EXCERPT)}")
        journal.remember(context, OperationRecord(OPEN, thread.id.value, null))
        return viewOf(thread, target).copy(written = comment.id)
    }

    @Transactional
    override fun reply(
        context: WriteContext,
        thread: ThreadId,
        body: String,
        markAnswered: Boolean,
    ): ThreadView {
        journal.replayed(context, REPLY)?.let { return replay(context.access, it) }
        val (current, document) = lockThread(context.access, thread)
        val now = journal.now()
        val comment = Comment.write(CommentId(UUID.randomUUID()), context.access.actor, body, now)
        val replied = current.reply(comment, now).let { if (markAnswered) it.markAnswered(now) ?: it else it }
        val saved = threads.update(replied)
        notify(saved, comment, document)
        journal.record(context, "comment.added", subjectOf(saved), "${document.header.title}: ${comment.body.take(EXCERPT)}")
        journal.remember(context, OperationRecord(REPLY, comment.id.value, null))
        return viewOf(saved, document).copy(written = comment.id)
    }

    @Transactional
    override fun editComment(
        context: WriteContext,
        comment: CommentId,
        body: String,
    ): ThreadView {
        val (current, document) = lockByComment(context.access, comment)
        val saved = threads.update(current.editComment(comment, context.access.actor, body, journal.now()))
        journal.record(context, "comment.edited", subjectOf(saved), "${document.header.title}: 댓글 수정")
        return viewOf(saved, document)
    }

    @Transactional
    override fun deleteComment(
        context: WriteContext,
        comment: CommentId,
    ): ThreadView {
        val (current, document) = lockByComment(context.access, comment)
        val saved = threads.update(current.deleteComment(comment, context.access.actor, journal.now()))
        journal.record(context, "comment.deleted", subjectOf(saved), "${document.header.title}: 댓글 삭제")
        return viewOf(saved, document)
    }

    // Everything is applied to the thread before it is saved once, so a refused status keeps the old anchor too.
    @Transactional
    override fun change(
        context: WriteContext,
        thread: ThreadId,
        change: ThreadChange,
    ): ThreadView {
        if (change.isEmpty) throw InvalidRequestException("pass status, anchor, or linkRevision")
        val (current, document) = lockThread(context.access, thread)
        val now = journal.now()
        val moved = change.anchor?.let { current.moveTo(AnchorPlacement.place(it, document), document.revision, now) } ?: current
        val updated = change.status?.let { statusChanged(moved, it, context.access.actor, now) } ?: moved
        val linked = change.linkRevision?.let { linked(updated, it, document, now) } ?: updated
        if (linked == current) return viewOf(current, document)
        val saved = threads.update(linked)
        val entries =
            listOfNotNull(
                Entry("thread.moved", "댓글 위치 변경", null).takeIf { moved != current },
                Entry("thread.status", "스레드 ${statusWord(saved.status)}", null).takeIf { updated.status != moved.status },
                Entry("thread.linked", "rev ${change.linkRevision} 연결", change.linkRevision).takeIf { linked != updated },
            )
        entries.forEach { journal.record(context, it.kind, subjectOf(saved, it.revision), "${document.header.title}: ${it.summary}") }
        return viewOf(saved, document)
    }

    // One activity log entry for a part of a thread change.
    private class Entry(
        val kind: String,
        val summary: String,
        val revision: Int?,
    )

    private fun statusChanged(
        thread: CommentThread,
        status: ThreadStatus,
        actor: Actor,
        now: Instant,
    ): CommentThread? =
        when (status) {
            ThreadStatus.OPEN -> thread.reopen(now)
            ThreadStatus.ANSWERED -> thread.markAnswered(now)
            ThreadStatus.RESOLVED -> thread.resolve(actor, now)
        }

    private fun linked(
        thread: CommentThread,
        revision: Int,
        document: Document,
        now: Instant,
    ): CommentThread? {
        if (revision !in 1..document.revision) {
            throw InvalidRequestException("revision $revision does not exist; the document is at revision ${document.revision}")
        }
        return thread.linkRevision(revision, now)
    }

    override fun forDocument(
        access: Access,
        document: DocumentId,
    ): List<ThreadView> {
        val target = documents.findById(document)?.takeIf { access.sees(it.spaceId) } ?: throw DocumentNotFoundException(document)
        return threads.findByDocument(target.id).map { viewOf(it, target) }
    }

    override fun query(
        access: Access,
        query: ThreadQuery,
    ): List<ThreadView> {
        val visible = (access.spaces as? SpaceScope.Listed)?.spaces
        val scope = if (visible == null) query.spaces else query.spaces?.intersect(visible) ?: visible
        val found =
            threads
                .find(
                    ThreadCriteria(query.copy(spaces = scope), ThreadQuery.MAX_RESULTS),
                ).filter { access.sees(it.location.spaceId) }
        val byId = documents.findByIds(found.map { it.location.documentId }.distinct()).associateBy { it.id }
        val spacesById = spaces.findByIds(byId.values.map { it.spaceId }.toSet()).associateBy { it.id }
        return found.mapNotNull { thread ->
            val document = byId[thread.location.documentId] ?: return@mapNotNull null
            spacesById[document.spaceId]?.let { contextualView(thread, document, it) }
        }
    }

    // The thread, then the document it belongs to, after checking the caller may comment there.
    private fun lockThread(
        access: Access,
        id: ThreadId,
    ): Pair<CommentThread, Document> {
        val thread = threads.findByIdForUpdate(id)?.takeIf { access.sees(it.location.spaceId) } ?: throw ThreadNotFoundException(id)
        return thread to commentable(access, thread)
    }

    private fun lockByComment(
        access: Access,
        id: CommentId,
    ): Pair<CommentThread, Document> {
        val thread = threads.findByCommentForUpdate(id)?.takeIf { access.sees(it.location.spaceId) } ?: throw CommentNotFoundException(id)
        return thread to commentable(access, thread)
    }

    private fun commentable(
        access: Access,
        thread: CommentThread,
    ): Document {
        Permissions.require(access, thread.location.spaceId, Permission.COMMENT, "commenting")
        return documents.findById(thread.location.documentId) ?: throw ThreadNotFoundException(thread.id)
    }

    private fun notify(
        thread: CommentThread,
        comment: Comment,
        document: Document,
    ) {
        val target = NoticeTarget(document.spaceId, document.id, thread.id, comment.id)
        val items =
            InboxRecipients.of(thread, comment, document.header.properties.assignee).flatMap { (kind, logins) ->
                logins.map {
                    InboxItem.deliver(
                        InboxItemId(UUID.randomUUID()),
                        it,
                        Notice.of(kind, target, comment.author, comment.body),
                        journal.now(),
                    )
                }
            }
        if (items.isNotEmpty()) inbox.addAll(items)
    }

    // A retried comment is not written again: the answer is the thread as it is now with the comment the first attempt
    // wrote, after the same visibility and permission checks a first attempt gets.
    private fun replay(
        access: Access,
        record: OperationRecord,
    ): ThreadView {
        val opened = record.kind == OPEN
        val found = if (opened) threads.findById(ThreadId(record.target)) else threads.findByCommentForUpdate(CommentId(record.target))
        val thread = found?.takeIf { access.sees(it.location.spaceId) } ?: throw ThreadNotFoundException(record.target)
        val written = if (opened) thread.comments.first().id else CommentId(record.target)
        return viewOf(thread, commentable(access, thread)).copy(written = written)
    }

    private fun viewOf(
        thread: CommentThread,
        document: Document,
    ): ThreadView {
        val anchor = thread.location.anchor
        val found = AnchorResolver.resolve(anchor, document.blocks)
        if (found != AnchorResolution.Detached || anchor !is Anchor.Text) return ThreadView(thread, found, null)
        return ThreadView(thread, AnchorResolver.resolve(anchor, document.blocks, anchoredText(thread, anchor)), null)
    }

    // The block's text in the revision the anchor was placed on, read only for a quote that is no longer found.
    private fun anchoredText(
        thread: CommentThread,
        anchor: Anchor.Text,
    ): String? =
        revisions
            .find(thread.location.documentId, thread.location.anchoredRevision)
            ?.content
            ?.blocks
            ?.firstOrNull { it.id == anchor.blockId }
            ?.plainText()

    private fun contextualView(
        thread: CommentThread,
        document: Document,
        space: Space,
    ): ThreadView {
        val position = anchoredIndex(thread, document)
        val blocks = document.blocks
        val context =
            ThreadContext(
                space = space.slug,
                path = document.header.path,
                title = document.header.title,
                anchorBlock = blocks.getOrNull(position)?.let(::annotated),
                previousBlock = blocks.getOrNull(position - 1)?.takeIf { position > 0 }?.let(::annotated),
                nextBlock = blocks.getOrNull(position + 1)?.takeIf { position >= 0 }?.let(::annotated),
            )
        return viewOf(thread, document).copy(context = context)
    }

    private fun anchoredIndex(
        thread: CommentThread,
        document: Document,
    ): Int {
        val anchor = thread.location.anchor
        val blockId =
            when (anchor) {
                is Anchor.Text -> anchor.blockId
                is Anchor.Cell -> anchor.blockId
                is Anchor.Whole -> anchor.blockId
                Anchor.Document -> return -1
            }
        return document.blocks.indexOfFirst { it.id == blockId }
    }

    private fun annotated(block: Block): String = BlockMarkdown.annotation(block) + "\n" + BlockMarkdown.of(block)

    private fun statusWord(status: ThreadStatus): String =
        when (status) {
            ThreadStatus.OPEN -> "다시 열림"
            ThreadStatus.ANSWERED -> "답변함"
            ThreadStatus.RESOLVED -> "해결됨"
        }

    private fun subjectOf(
        thread: CommentThread,
        revision: Int? = null,
    ): ActivitySubject = ActivitySubject(thread.location.spaceId.value, thread.location.documentId.value, thread.id.value, revision)

    private companion object {
        const val OPEN = "thread.open"
        const val REPLY = "thread.reply"
        const val EXCERPT = 80
    }
}
