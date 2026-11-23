package xyz.robinjoon.agentdocs.workspace.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.application.model.ActivitySubject
import xyz.robinjoon.agentdocs.application.model.CreatedComment
import xyz.robinjoon.agentdocs.workspace.application.model.AnchorRequest
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.OperationRecord
import xyz.robinjoon.agentdocs.workspace.application.model.OperationResultState
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadChange
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadContext
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadCriteria
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadQuery
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadView
import xyz.robinjoon.agentdocs.workspace.application.model.WorkspaceActivity
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.input.CommentNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.LegacyOperationException
import xyz.robinjoon.agentdocs.workspace.application.port.input.ResultRemovedException
import xyz.robinjoon.agentdocs.workspace.application.port.input.ThreadNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.ThreadUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.InboxRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.RevisionCatalog
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.ThreadRepository
import xyz.robinjoon.agentdocs.workspace.domain.Access
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
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadActionException
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
        val locked =
            documents.findByIdForUpdate(document)?.takeIf { context.access.sees(it.spaceId) } ?: throw DocumentNotFoundException(document)
        Permissions.require(context.access, locked.spaceId, Permission.COMMENT, "commenting")
        val target = PendingEdits(documents, journal).confirm(locked)
        val now = journal.now()
        val location = ThreadLocation(target.spaceId, target.id, AnchorPlacement.place(anchor, target), target.revision)
        val comment = Comment.write(CommentId(UUID.randomUUID()), context.access.actor, body, now)
        val thread = threads.add(CommentThread.open(ThreadId(UUID.randomUUID()), location, comment, now))
        notify(thread, comment, target)
        journal.record(
            context,
            WorkspaceActivity(
                "thread.opened",
                subjectOf(thread, target.revision),
                "${target.header.title}: ${comment.body.take(EXCERPT)}",
                createdComment(thread, comment, target),
            ),
        )
        return remembered(context, OperationRecord(OPEN, thread.id.value, null), viewOf(thread, target).copy(written = comment.id))
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
        current.requireAvailable(context.access.actor, journal.now())
        val now = journal.now()
        val comment = Comment.write(CommentId(UUID.randomUUID()), context.access.actor, body, now)
        val replied = current.reply(comment, now).let { if (markAnswered) it.markAnswered(now) ?: it else it }
        val saved = threads.update(replied)
        notify(saved, comment, document)
        journal.record(
            context,
            WorkspaceActivity(
                "comment.added",
                subjectOf(saved, document.revision),
                "${document.header.title}: ${comment.body.take(EXCERPT)}",
                createdComment(saved, comment, document),
            ),
        )
        return remembered(context, OperationRecord(REPLY, comment.id.value, null), viewOf(saved, document).copy(written = comment.id))
    }

    @Transactional
    override fun editComment(
        context: WriteContext,
        comment: CommentId,
        body: String,
    ): ThreadView {
        journal.replayed(context, EDIT_COMMENT)?.let { return replay(context.access, it) }
        val (current, document) = lockByComment(context.access, comment)
        if (current.collaboration.suggestions.any { it.comment == comment }) current.requireAvailable(context.access.actor, journal.now())
        val saved = threads.update(current.editComment(comment, context.access.actor, body, journal.now()))
        journal.record(context, "comment.edited", subjectOf(saved, document.revision), "${document.header.title}: 댓글 수정")
        return remembered(context, OperationRecord(EDIT_COMMENT, comment.value, null), viewOf(saved, document))
    }

    @Transactional
    override fun deleteComment(
        context: WriteContext,
        comment: CommentId,
    ): ThreadView {
        journal.replayed(context, DELETE_COMMENT)?.let { return replay(context.access, it) }
        val (current, document) = lockByComment(context.access, comment)
        if (current.collaboration.suggestions.any { it.comment == comment }) current.requireAvailable(context.access.actor, journal.now())
        val saved = threads.update(current.deleteComment(comment, context.access.actor, journal.now()))
        inbox.redactCommentExcerpt(comment)
        journal.removeCommentCopies(comment)
        journal.record(context, "comment.deleted", subjectOf(saved, document.revision), "${document.header.title}: 댓글 삭제")
        return remembered(context, OperationRecord(DELETE_COMMENT, comment.value, null), viewOf(saved, document))
    }

    // Everything is applied to the thread before it is saved once, so a refused status keeps the old anchor too.
    @Transactional
    override fun change(
        context: WriteContext,
        thread: ThreadId,
        change: ThreadChange,
    ): ThreadView {
        if (change.isEmpty) throw InvalidRequestException("pass status, anchor, or linkRevision")
        journal.replayed(context, CHANGE)?.let { return replay(context.access, it) }
        val (current, document) =
            if (change.anchor == null) lockThread(context.access, thread) else lockForAnchor(context.access, thread)
        val now = journal.now()
        if (change.linkRevision != null || change.status == ThreadStatus.ANSWERED) current.requireAvailable(context.access.actor, now)
        val moved = change.anchor?.let { current.moveTo(AnchorPlacement.place(it, document), document.revision, now) } ?: current
        val updated = change.status?.let { statusChanged(moved, it, context.access, now) } ?: moved
        val linked = change.linkRevision?.let { linked(updated, it, document, now) } ?: updated
        if (linked == current) return remembered(context, OperationRecord(CHANGE, thread.value, null), viewOf(current, document))
        val saved = threads.update(linked)
        val entries =
            listOfNotNull(
                Entry("thread.moved", "댓글 위치 변경", null).takeIf { moved != current },
                Entry("thread.status", "스레드 ${statusWord(saved.status)}", null).takeIf { updated.status != moved.status },
                Entry("thread.linked", "rev ${change.linkRevision} 연결", change.linkRevision).takeIf { linked != updated },
            )
        entries.forEach {
            journal.record(context, it.kind, subjectOf(saved, it.revision ?: document.revision), "${document.header.title}: ${it.summary}")
        }
        return remembered(context, OperationRecord(CHANGE, thread.value, null), viewOf(saved, document))
    }

    // One activity log entry for a part of a thread change.
    private class Entry(
        val kind: String,
        val summary: String,
        val revision: Int?,
    )

    // A person who may comment but not edit the space resolves only their own threads. Agents keep their own rule in
    // the thread: they never resolve a thread a person opened.
    private fun statusChanged(
        thread: CommentThread,
        status: ThreadStatus,
        access: Access,
        now: Instant,
    ): CommentThread? =
        when (status) {
            ThreadStatus.OPEN -> {
                thread.reopen(now)
            }

            ThreadStatus.ANSWERED -> {
                thread.markAnswered(now)
            }

            ThreadStatus.RESOLVED -> {
                val onlyOwn = !access.actor.isAgent && !access.allows(thread.location.spaceId, Permission.EDIT)
                thread.resolve(access.actor, now, onlyOwn)
            }
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

    @Transactional
    override fun react(
        context: WriteContext,
        thread: ThreadId,
        emoji: String,
        add: Boolean,
    ): ThreadView {
        journal.replayed(context, REACT)?.let { return replay(context.access, it) }
        val (current, document) = lockThread(context.access, thread)
        val next = current.collaborate(current.collaboration.react(context.access.actor, emoji, add), journal.now())
        val saved = threads.update(next)
        journal.record(context, "thread.reaction", subjectOf(saved, document.revision), "스레드 반응 변경")
        return remembered(context, OperationRecord(REACT, thread.value, null), viewOf(saved, document))
    }

    @Transactional
    override fun claim(
        context: WriteContext,
        thread: ThreadId,
        action: String,
    ): ThreadView {
        journal.replayed(context, CLAIM)?.let { return replay(context.access, it) }
        val (current, document) = lockThread(context.access, thread)
        if (current.status != ThreadStatus.OPEN) throw InvalidRequestException("only open threads can be claimed")
        val now = journal.now()
        val next = current.collaborate(current.collaboration.claim(context.access.actor, now, action), now)
        val saved = threads.update(next)
        journal.record(context, "thread.claim", subjectOf(saved, document.revision), "스레드 점유 변경")
        return remembered(context, OperationRecord(CLAIM, thread.value, null), viewOf(saved, document))
    }

    override fun forDocument(
        access: Access,
        document: DocumentId,
        flowTargets: Boolean,
    ): List<ThreadView> {
        val target = documents.findById(document)?.takeIf { access.sees(it.spaceId) } ?: throw DocumentNotFoundException(document)
        val views = threads.findByDocument(target.id).map { viewOf(it, target) }
        if (!flowTargets || target.header.type != "flow") return views
        val cache = HashMap<Pair<DocumentId, Int>, List<Block>>()
        return views.map { withFlowTarget(it, target, cache) }
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
        val flowRevisions = HashMap<Pair<DocumentId, Int>, List<Block>>()
        return found.mapNotNull { thread ->
            val document = byId[thread.location.documentId] ?: return@mapNotNull null
            spacesById[document.spaceId]?.let { contextualView(thread, document, it) }?.let { view ->
                if (!query.flowTargets || document.header.type != "flow") view else withFlowTarget(view, document, flowRevisions)
            }
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

    // Read the immutable document identity first; acquire both write locks in document-to-thread order.
    private fun lockForAnchor(
        access: Access,
        id: ThreadId,
    ): Pair<CommentThread, Document> {
        val known = threads.findById(id)?.takeIf { access.sees(it.location.spaceId) } ?: throw ThreadNotFoundException(id)
        val document = documents.findByIdForUpdate(known.location.documentId) ?: throw ThreadNotFoundException(id)
        if (!access.sees(document.spaceId)) throw ThreadNotFoundException(id)
        Permissions.require(access, document.spaceId, Permission.COMMENT, "commenting")
        val thread = threads.findByIdForUpdate(id)?.takeIf { access.sees(it.location.spaceId) } ?: throw ThreadNotFoundException(id)
        return thread to PendingEdits(documents, journal).confirm(document)
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

    // Recheck current access before replaying the first result. Legacy rows have no snapshot.
    private fun replay(
        access: Access,
        record: OperationRecord,
    ): ThreadView {
        val byThread = record.kind in setOf(OPEN, CHANGE, REACT, CLAIM)
        val found = if (byThread) threads.findById(ThreadId(record.target)) else threads.findByCommentForUpdate(CommentId(record.target))
        val thread = found ?: rejectMissingThreadReplay(access, record, Permission.COMMENT)
        if (!access.sees(thread.location.spaceId)) throw ThreadNotFoundException(record.target)
        commentable(access, thread)
        if (record.kind == EDIT_COMMENT || record.kind == DELETE_COMMENT) requireAuthor(access, thread, CommentId(record.target))
        if (record.state == OperationResultState.REMOVED) throw ResultRemovedException(record)
        return record.threadView ?: throw LegacyOperationException(record)
    }

    private fun remembered(
        context: WriteContext,
        record: OperationRecord,
        view: ThreadView,
    ): ThreadView {
        journal.remember(context, record.copy(threadView = view))
        return view
    }

    // A deleted comment still has an author; a successful delete retry must remain replayable.
    private fun requireAuthor(
        access: Access,
        thread: CommentThread,
        id: CommentId,
    ) {
        val comment = thread.comment(id) ?: throw CommentNotFoundException(id)
        if (!comment.author.isSameAuthorAs(access.actor)) {
            throw ThreadActionException("only the author can change a comment", "reply with a new comment instead")
        }
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

    private fun withFlowTarget(
        view: ThreadView,
        document: Document,
        cache: MutableMap<Pair<DocumentId, Int>, List<Block>>,
    ): ThreadView {
        val key = document.id to view.thread.location.anchoredRevision
        val earlier =
            cache.getOrPut(key) {
                revisions
                    .find(key.first, key.second)
                    ?.content
                    ?.blocks
                    .orEmpty()
            }
        val target =
            xyz.robinjoon.agentdocs.workspace.domain.thread.FlowAnchorTarget.of(
                view.thread.location.anchor,
                earlier,
                document.blocks,
            )
        return view.copy(target = target, targetVersion = document.version)
    }

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
        revision: Int,
    ): ActivitySubject = ActivitySubject(thread.location.spaceId.value, thread.location.documentId.value, thread.id.value, revision)

    private companion object {
        const val REACT = "thread.react"
        const val CLAIM = "thread.claim"
        const val OPEN = "thread.open"
        const val REPLY = "thread.reply"
        const val CHANGE = "thread.change"
        const val EDIT_COMMENT = "comment.edit"
        const val DELETE_COMMENT = "comment.delete"
        const val EXCERPT = 80
    }
}
