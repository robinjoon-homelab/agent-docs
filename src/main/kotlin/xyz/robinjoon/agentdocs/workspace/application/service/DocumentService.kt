package xyz.robinjoon.agentdocs.workspace.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.application.model.ActivitySubject
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentDraft
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentWrite
import xyz.robinjoon.agentdocs.workspace.application.model.HeaderChange
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.OperationRecord
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.RevisionNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.SpaceNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.StaleRevisionException
import xyz.robinjoon.agentdocs.workspace.application.port.input.ThreadNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.RevisionCatalog
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.ThreadRepository
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.Permission
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.ChangeSource
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentHeader
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentProperties
import xyz.robinjoon.agentdocs.workspace.domain.document.PathTakenException
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.BlockMerge
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.DocumentEditor
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.EditConflictException
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.EditOperation
import xyz.robinjoon.agentdocs.workspace.domain.document.history.BlockDiff
import xyz.robinjoon.agentdocs.workspace.domain.document.history.ChangeOrigin
import xyz.robinjoon.agentdocs.workspace.domain.document.history.ChangeSummary
import xyz.robinjoon.agentdocs.workspace.domain.document.history.Revision
import xyz.robinjoon.agentdocs.workspace.domain.document.history.RevisionChange
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentThread
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Transactional(readOnly = true)
class DocumentService(
    private val documents: DocumentRepository,
    private val revisions: RevisionCatalog,
    private val spaces: SpaceRepository,
    private val threads: ThreadRepository,
    private val journal: WriteJournal,
) : DocumentUseCase {
    @Transactional
    override fun create(
        context: WriteContext,
        space: SpaceSlug,
        draft: DocumentDraft,
    ): DocumentWrite {
        journal.replayed(context, CREATE)?.let { return replay(context.access, it) }
        val target = spaces.findBySlug(space)?.takeIf { context.access.sees(it.id) } ?: throw SpaceNotFoundException(space)
        Permissions.require(context.access, target.id, Permission.EDIT, "writing documents")
        requirePathFree(target.id, draft.path, null)
        val now = journal.now()
        val header = DocumentHeader(target.id, draft.path, draft.title.trim(), draft.properties)
        val document = Document.create(DocumentId(UUID.randomUUID()), header, draft.blocks, now)
        val change = RevisionChange.of(originOf(context), context.summary ?: "문서 생성", document.blocks.map { it.id }, now)
        val saved = documents.add(document, Revision.of(document, change))
        journal.record(context, "document.created", subjectOf(saved), "${saved.header.title}: ${change.summary}")
        return remember(context, CREATE, DocumentWrite(saved, saved.revision, change.changedBlocks, amended = false, unchanged = false))
    }

    @Transactional
    override fun save(
        context: WriteContext,
        id: DocumentId,
        baseRevision: Int,
        blocks: List<Block>,
    ): DocumentWrite {
        journal.replayed(context, SAVE)?.let { return replay(context.access, it) }
        val document = lockForEdit(context.access, id)
        val stale = baseRevision != document.revision
        val written = write(context, document, Content(document.header, mergeOnto(document, baseRevision, blocks)), amendable = !stale)
        return remember(context, SAVE, written.copy(merged = stale))
    }

    @Transactional
    override fun edit(
        context: WriteContext,
        id: DocumentId,
        operations: List<EditOperation>,
        answering: ThreadId?,
    ): DocumentWrite {
        journal.replayed(context, EDIT)?.let { return replay(context.access, it) }
        val document = lockForEdit(context.access, id)
        val thread = answering?.let { lockThreadOf(document, it) }
        val result =
            write(context, document, Content(document.header, DocumentEditor.apply(document.blocks, operations)), amendable = false)
        if (thread != null && !result.unchanged) linkAnswer(context, thread, result)
        return remember(context, EDIT, result)
    }

    @Transactional
    override fun changeHeader(
        context: WriteContext,
        id: DocumentId,
        change: HeaderChange,
    ): DocumentWrite {
        journal.replayed(context, HEADER)?.let { return replay(context.access, it) }
        val document = lockForEdit(context.access, id)
        change.baseRevision?.takeIf { it != document.revision }?.let { requireUntouchedSince(document, it, change) }
        val header = headerWith(document.header, change)
        if (header.path != document.header.path) requirePathFree(document.spaceId, header.path, document.id)
        return remember(context, HEADER, write(context, document, Content(header, document.blocks), amendable = true))
    }

    @Transactional
    override fun trash(
        context: WriteContext,
        id: DocumentId,
    ): DocumentWrite = transition(context, id, Transition.TRASH)

    @Transactional
    override fun untrash(
        context: WriteContext,
        id: DocumentId,
    ): DocumentWrite = transition(context, id, Transition.UNTRASH)

    @Transactional
    override fun purge(
        context: WriteContext,
        id: DocumentId,
    ) {
        val document = documents.findByIdForUpdate(id)?.takeIf { context.access.sees(it.spaceId) } ?: throw DocumentNotFoundException(id)
        Permissions.requireOwner(context.access, "deleting a document permanently")
        if (!document.isTrashed) {
            throw InvalidRequestException("only a document in the trash can be deleted permanently", "move it to the trash first")
        }
        documents.delete(id)
        journal.record(context, "document.purged", subjectOf(document), "${document.header.title}: 영구 삭제")
    }

    @Transactional
    override fun restore(
        context: WriteContext,
        id: DocumentId,
        revision: Int,
        baseRevision: Int?,
    ): DocumentWrite {
        journal.replayed(context, RESTORE)?.let { return replay(context.access, it) }
        val document = lockForEdit(context.access, id)
        if (baseRevision != null && baseRevision != document.revision) throw StaleRevisionException(baseRevision, document.revision)
        val target = revisions.find(id, revision) ?: throw RevisionNotFoundException("$id rev $revision")
        val header = document.header.copy(title = target.content.header.title)
        val restoring = context.copy(summary = context.summary ?: "rev $revision 내용으로 복원")
        return remember(context, RESTORE, write(restoring, document, Content(header, target.content.blocks), amendable = false))
    }

    @Transactional
    override fun purgeExpiredTrash(): Int {
        val expired = documents.trashedBefore(journal.now().minus(trashRetention))
        expired.forEach { id ->
            val document = documents.findByIdForUpdate(id) ?: return@forEach
            documents.delete(id)
            journal.recordBySystem("document.purged", subjectOf(document), "${document.header.title}: 휴지통 보관 기간이 지나 삭제")
        }
        return expired.size
    }

    private fun transition(
        context: WriteContext,
        id: DocumentId,
        transition: Transition,
    ): DocumentWrite {
        val document = lockForEdit(context.access, id)
        val now = journal.now()
        val moved = transition.apply(document, now) ?: return unchanged(document)
        val change = RevisionChange.of(originOf(context), transition.summary, emptyList(), now)
        val saved = documents.update(moved, Revision.of(moved, change), amended = false)
        journal.record(context, transition.kind, subjectOf(saved), "${saved.header.title}: ${transition.summary}")
        return DocumentWrite(saved, saved.revision, emptyList(), amended = false, unchanged = false)
    }

    // A write that changes nothing adds no revision.
    private fun write(
        context: WriteContext,
        document: Document,
        next: Content,
        amendable: Boolean,
    ): DocumentWrite {
        // Compared as whole lists: the diff ignores moved blocks, but a new order is still a change.
        if (next.blocks == document.blocks && next.header == document.header) return unchanged(document)
        val changes = BlockDiff.between(document.blocks, next.blocks)
        val now = journal.now()
        val latest = if (amendable) amendableRevision(context, document, now) else null
        if (latest != null) return amend(context, document, latest, next)
        val revised = document.revise(next.header, next.blocks, now)
        val summary =
            context.summary ?: ChangeSummary.of(document.header, next.header, changes, BlockDiff.reordered(document.blocks, next.blocks))
        val change = RevisionChange.of(originOf(context), summary, changes.map { it.blockId }, now)
        val saved = documents.update(revised, Revision.of(revised, change), amended = false)
        journal.record(context, "document.revised", subjectOf(saved), "${saved.header.title}: ${change.summary}")
        return DocumentWrite(saved, saved.revision, change.changedBlocks, amended = false, unchanged = false)
    }

    // A person's web saves group into one revision while they keep typing: the latest revision is theirs, came from
    // the web, and was last saved at most 30 seconds ago. Agents and other paths always add a revision.
    private fun amendableRevision(
        context: WriteContext,
        document: Document,
        now: Instant,
    ): Revision? {
        val actor = context.access.actor
        if (context.source != ChangeSource.WEB || actor.isAgent) return null
        val latest = revisions.find(document.id, document.revision) ?: return null
        val origin = latest.change.origin
        val recent = Duration.between(latest.change.period.updatedAt, now) <= amendWindow
        return latest.takeIf {
            recent && origin.source == ChangeSource.WEB &&
                origin.actor.isSameAuthorAs(
                    actor,
                ) && !latest.content.trashed
        }
    }

    // The summary and changed blocks of an amended revision describe its difference from the revision before it.
    private fun amend(
        context: WriteContext,
        document: Document,
        latest: Revision,
        next: Content,
    ): DocumentWrite {
        val now = journal.now()
        val amended = document.amend(next.header, next.blocks, now)
        val previous = if (latest.number > 1) revisions.find(document.id, latest.number - 1) else null
        val summary =
            previous?.let {
                val before = it.content.blocks
                ChangeSummary.of(
                    it.content.header,
                    next.header,
                    BlockDiff.between(before, next.blocks),
                    BlockDiff.reordered(before, next.blocks),
                )
            } ?: latest.change.summary
        val changed = BlockDiff.between(document.blocks, next.blocks).map { it.blockId }
        val change = latest.change.amendedWith(RevisionChange.of(originOf(context), summary, changed, now), summary)
        val saved = documents.update(amended, Revision.of(amended, change), amended = true)
        return DocumentWrite(saved, saved.revision, change.changedBlocks, amended = true, unchanged = false)
    }

    private fun mergeOnto(
        document: Document,
        baseRevision: Int,
        submitted: List<Block>,
    ): List<Block> {
        if (baseRevision == document.revision) return submitted
        if (baseRevision !in 1 until document.revision) {
            throw InvalidRequestException(
                "baseRevision $baseRevision does not exist; the document is at revision ${document.revision}",
                "send the revision you last read",
            )
        }
        val base = revisions.find(document.id, baseRevision) ?: throw RevisionNotFoundException("${document.id} rev $baseRevision")
        val merged = BlockMerge.merge(base.content.blocks, document.blocks, submitted)
        if (merged.conflicts.isNotEmpty()) throw EditConflictException(merged.conflicts)
        return merged.blocks
    }

    // A stale base conflicts only on the fields this change sets that someone changed since: renaming after an agent
    // edited blocks goes through, overwriting a title a person just typed does not.
    private fun requireUntouchedSince(
        document: Document,
        base: Int,
        change: HeaderChange,
    ) {
        val before = revisions.find(document.id, base)?.content?.header ?: throw StaleRevisionException(base, document.revision)
        val now = document.header
        val touched =
            listOf(
                change.title != null && before.title != now.title,
                change.path != null && before.path != now.path,
                change.status != null && before.properties.status != now.properties.status,
                change.tags != null && before.properties.tags != now.properties.tags,
                change.assignee != null && before.properties.assignee != now.properties.assignee,
            )
        if (touched.any { it }) throw StaleRevisionException(base, document.revision)
    }

    private fun headerWith(
        header: DocumentHeader,
        change: HeaderChange,
    ): DocumentHeader {
        val properties = header.properties
        val assignee = if (change.assignee == null) properties.assignee else change.assignee.login?.ifBlank { null }
        val next = DocumentProperties.of(change.tags ?: properties.tags, change.status ?: properties.status, assignee)
        return DocumentHeader(header.spaceId, change.path ?: header.path, change.title?.trim() ?: header.title, next)
    }

    private fun lockForEdit(
        access: Access,
        id: DocumentId,
    ): Document {
        val document = documents.findByIdForUpdate(id)?.takeIf { access.sees(it.spaceId) } ?: throw DocumentNotFoundException(id)
        Permissions.require(access, document.spaceId, Permission.EDIT, "editing a document")
        return document
    }

    private fun linkAnswer(
        context: WriteContext,
        thread: CommentThread,
        result: DocumentWrite,
    ) {
        val linked = thread.linkRevision(result.revision, journal.now())?.let(threads::update) ?: return
        val subject = ActivitySubject(linked.location.spaceId.value, linked.location.documentId.value, linked.id.value, result.revision)
        journal.record(context, "thread.linked", subject, "${result.document.header.title}: rev ${result.revision} 연결")
    }

    private fun lockThreadOf(
        document: Document,
        id: ThreadId,
    ): CommentThread = threads.findByIdForUpdate(id)?.takeIf { it.location.documentId == document.id } ?: throw ThreadNotFoundException(id)

    private fun requirePathFree(
        space: SpaceId,
        path: DocumentPath,
        self: DocumentId?,
    ) {
        val holder = documents.findByPath(space, path)
        if (holder != null && holder.id != self) throw PathTakenException(path)
    }

    private fun remember(
        context: WriteContext,
        kind: String,
        result: DocumentWrite,
    ): DocumentWrite {
        // A write that changed nothing is not remembered: its retry changes nothing either.
        if (!result.unchanged) journal.remember(context, OperationRecord(kind, result.document.id.value, result.revision))
        return result
    }

    // A retried write does not write again. It answers with the document as it is now and the revision the first
    // attempt produced, after the same visibility and permission checks a first attempt gets.
    private fun replay(
        access: Access,
        record: OperationRecord,
    ): DocumentWrite {
        val id = DocumentId(record.target)
        val document = documents.findById(id)?.takeIf { access.sees(it.spaceId) } ?: throw DocumentNotFoundException(id)
        Permissions.require(access, document.spaceId, Permission.EDIT, "editing a document")
        val revision = record.revision ?: document.revision
        val changed =
            revisions
                .find(id, revision)
                ?.change
                ?.changedBlocks
                .orEmpty()
        return DocumentWrite(document, revision, changed, amended = false, unchanged = false)
    }

    private fun unchanged(document: Document): DocumentWrite =
        DocumentWrite(document, document.revision, emptyList(), amended = false, unchanged = true)

    private fun originOf(context: WriteContext): ChangeOrigin = ChangeOrigin(context.access.actor, context.source)

    private fun subjectOf(document: Document): ActivitySubject =
        ActivitySubject(document.spaceId.value, document.id.value, null, document.revision)

    private data class Content(
        val header: DocumentHeader,
        val blocks: List<Block>,
    )

    private enum class Transition(
        val kind: String,
        val summary: String,
    ) {
        TRASH("document.trashed", "휴지통으로 이동") {
            override fun apply(
                document: Document,
                at: Instant,
            ): Document? = document.trash(at)
        },
        UNTRASH("document.untrashed", "휴지통에서 복원") {
            override fun apply(
                document: Document,
                at: Instant,
            ): Document? = document.untrash(at)
        },
        ;

        abstract fun apply(
            document: Document,
            at: Instant,
        ): Document?
    }

    private companion object {
        const val CREATE = "document.create"
        const val SAVE = "document.save"
        const val EDIT = "document.edit"
        const val HEADER = "document.header"
        const val RESTORE = "document.restore"
        val amendWindow: Duration = Duration.ofSeconds(30)
        val trashRetention: Duration = Duration.ofDays(30)
    }
}
