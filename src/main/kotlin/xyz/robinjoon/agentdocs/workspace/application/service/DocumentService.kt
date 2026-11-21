package xyz.robinjoon.agentdocs.workspace.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.application.model.ActivitySubject
import xyz.robinjoon.agentdocs.workspace.application.model.BlockRestore
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentDraft
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentWrite
import xyz.robinjoon.agentdocs.workspace.application.model.HeaderChange
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.OperationRecord
import xyz.robinjoon.agentdocs.workspace.application.model.OperationResultState
import xyz.robinjoon.agentdocs.workspace.application.model.RevisionLabel
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceReference
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.LegacyOperationException
import xyz.robinjoon.agentdocs.workspace.application.port.input.ResultRemovedException
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
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.DocumentEditor
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.EditOperation
import xyz.robinjoon.agentdocs.workspace.domain.document.history.BlockDiff
import xyz.robinjoon.agentdocs.workspace.domain.document.history.BlockRestoration
import xyz.robinjoon.agentdocs.workspace.domain.document.history.ChangeOrigin
import xyz.robinjoon.agentdocs.workspace.domain.document.history.ChangeSummary
import xyz.robinjoon.agentdocs.workspace.domain.document.history.Revision
import xyz.robinjoon.agentdocs.workspace.domain.document.history.RevisionChange
import xyz.robinjoon.agentdocs.workspace.domain.document.history.RevisionKind
import xyz.robinjoon.agentdocs.workspace.domain.document.history.RevisionName
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
    private val tree = DocumentTree(documents, journal)
    private val pending = PendingEdits(documents, journal)

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
        val parents = tree.fillParents(context, target.id, draft.path)
        val now = journal.now()
        val header = DocumentHeader(target.id, draft.path, draft.title.trim(), draft.properties, draft.type)
        val document = Document.create(DocumentId(UUID.randomUUID()), header, draft.blocks, now)
        val change = RevisionChange.of(originOf(context, target.id), context.summary ?: "문서 생성", document.blocks.map { it.id }, now)
        val saved = documents.add(document, Revision.of(document, change))
        journal.confirmed(saved, change)
        journal.record(context, "document.created", subjectOf(saved), "${saved.header.title}: ${change.summary}")
        val written =
            DocumentWrite(
                saved,
                saved.revision,
                change.changedBlocks,
                amended = false,
                unchanged = false,
                related = parents,
                space = SpaceReference(target.id, target.slug, target.profile.name),
            )
        return remember(context, CREATE, written)
    }

    @Transactional
    override fun save(
        context: WriteContext,
        id: DocumentId,
        baseVersion: Int,
        blocks: List<Block>,
    ): DocumentWrite {
        journal.replayed(context, SAVE)?.let { return replay(context.access, it) }
        val document = lockForEdit(context.access, id)
        requireVersion(document, baseVersion)
        val written = write(context, document, Content(document.header, blocks), WriteMode.TYPING)
        return remember(context, SAVE, written)
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
        thread?.requireAvailable(context.access.actor, journal.now())
        val result =
            write(context, document, Content(document.header, DocumentEditor.apply(document.blocks, operations)), WriteMode.EDIT)
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
        requireVersion(document, change.baseVersion ?: throw InvalidRequestException("baseVersion is required"))
        val header = headerWith(document.header, change)
        val content = Content(header, document.blocks)
        if (header.path == document.header.path) return remember(context, HEADER, write(context, document, content, WriteMode.TYPING))
        // A new path takes the documents under it along and needs a parent there, so it is checked whole first.
        val moves = tree.planMove(document, header.path)
        val parents = tree.fillParents(context, document.spaceId, header.path)
        val written = write(context, document, content, WriteMode.TYPING)
        return remember(context, HEADER, written.copy(related = parents + tree.moveAlong(context, moves)))
    }

    @Transactional
    override fun trash(
        context: WriteContext,
        id: DocumentId,
    ): DocumentWrite {
        journal.replayed(context, Transition.TRASH.operation)?.let { return replay(context.access, it) }
        val document = lockForEdit(context.access, id)
        if (document.isTrashed) return remember(context, Transition.TRASH.operation, unchanged(document))
        val now = journal.now()
        val written = transition(context, document, Transition.TRASH, now)
        return remember(context, Transition.TRASH.operation, written.copy(related = tree.trashAlong(context, document, now)))
    }

    // Brings back the documents trashed together with this one, and the parents it needs.
    @Transactional
    override fun untrash(
        context: WriteContext,
        id: DocumentId,
    ): DocumentWrite {
        journal.replayed(context, Transition.UNTRASH.operation)?.let { return replay(context.access, it) }
        val document = lockForEdit(context.access, id)
        if (!document.isTrashed) return remember(context, Transition.UNTRASH.operation, unchanged(document))
        val together = tree.planRestore(document)
        val now = journal.now()
        val parents = tree.reviveParents(context, document.spaceId, document.header.path, now)
        val written = transition(context, document, Transition.UNTRASH, now)
        val related = parents + tree.restoreAlong(context, together, now)
        return remember(context, Transition.UNTRASH.operation, written.copy(related = related))
    }

    @Transactional
    override fun purge(
        context: WriteContext,
        id: DocumentId,
    ) {
        Permissions.requireOwner(context.access, "deleting a document permanently")
        // The document is gone after the first attempt, so a retry has nothing left to answer with.
        journal.replayed(context, PURGE)?.let {
            if (it.state == OperationResultState.REMOVED) throw ResultRemovedException(it)
            if (it.state != OperationResultState.COMPLETED) throw LegacyOperationException(it)
            return
        }
        val document = documents.findByIdForUpdate(id)?.takeIf { context.access.sees(it.spaceId) } ?: throw DocumentNotFoundException(id)
        if (!document.isTrashed) {
            throw InvalidRequestException("only a document in the trash can be deleted permanently", "move it to the trash first")
        }
        val together = documents.lockTrashedUnder(document.spaceId, document.header.path)
        val deletingSpaces = (listOf(document) + together).associate { it.id to it.spaceId }
        documents.delete(id)
        journal.record(context, "document.purged", subjectOf(document), "${document.header.title}: 영구 삭제")
        tree.purgeAlong(context, together)
        // Thread deletion waits for in-flight writers before their copied results are removed.
        journal.removeDocumentResults(deletingSpaces)
        journal.remember(context, OperationRecord(PURGE, id.value, null, state = OperationResultState.COMPLETED))
    }

    @Transactional
    override fun restore(
        context: WriteContext,
        id: DocumentId,
        revision: Int,
        baseVersion: Int?,
    ): DocumentWrite {
        journal.replayed(context, RESTORE)?.let { return replay(context.access, it) }
        val document = lockForEdit(context.access, id)
        requireVersion(document, baseVersion ?: throw InvalidRequestException("baseVersion is required"))
        val target = revisions.find(id, revision) ?: throw RevisionNotFoundException("$id rev $revision")
        val header = document.header.copy(title = target.content.header.title, properties = target.content.header.properties)
        val restoring = context.copy(summary = context.summary ?: "rev $revision 내용으로 복원")
        return remember(context, RESTORE, write(restoring, document, Content(header, target.content.blocks), WriteMode.RESTORE))
    }

    @Transactional
    override fun nameRevision(
        context: WriteContext,
        id: DocumentId,
        revision: Int,
        name: String,
    ): RevisionLabel {
        val recorded = journal.replayed(context, LABEL)
        if (recorded != null) {
            DocumentReplay(documents).requireEditable(context.access, recorded, DocumentId(recorded.target))
            return recorded.revisionLabel ?: throw LegacyOperationException(recorded)
        }
        val document = lockForEdit(context.access, id)
        val target = revisions.find(id, revision) ?: throw RevisionNotFoundException("$id rev $revision")
        val normalized =
            RevisionName
                .normalize(name)
        if (target.name != normalized) {
            documents.nameRevision(id, revision, normalized)
            journal.record(
                context,
                "revision.named",
                ActivitySubject(
                    document.spaceId.value,
                    document.id.value,
                    null,
                    revision,
                ),
                "rev $revision 이름 변경",
            )
        }
        val label =
            RevisionLabel(id, revision, normalized)
        journal.remember(context, OperationRecord(LABEL, id.value, revision, state = OperationResultState.AVAILABLE, revisionLabel = label))
        return label
    }

    @Transactional
    override fun restoreBlock(
        context: WriteContext,
        id: DocumentId,
        request: BlockRestore,
    ): DocumentWrite {
        journal.replayed(context, RESTORE_BLOCK)?.let { return replay(context.access, it) }
        val document = lockForEdit(context.access, id)
        requireVersion(document, request.baseVersion)
        val target = revisions.find(id, request.revision) ?: throw RevisionNotFoundException("$id rev ${request.revision}")
        val blocks =
            BlockRestoration.restore(
                document.blocks,
                target.content.blocks,
                request.blockId,
            )
        val restoring = context.copy(summary = context.summary ?: "rev ${request.revision} 블록 ${request.blockId} 복원")
        return remember(context, RESTORE_BLOCK, write(restoring, document, Content(document.header, blocks), WriteMode.RESTORE))
    }

    @Transactional
    override fun purgeExpiredTrash(): Int {
        val cutoff = journal.now().minus(trashRetention)
        val expired = documents.trashedBefore(cutoff)
        var deleted = 0
        expired.forEach { id ->
            val document =
                documents.findByIdForUpdate(id)?.takeIf {
                    it.lifecycle.trashedAt?.isBefore(cutoff) == true
                } ?: return@forEach
            documents.delete(id)
            journal.removeDocumentResults(mapOf(id to document.spaceId))
            journal.recordBySystem("document.purged", subjectOf(document), "${document.header.title}: 휴지통 보관 기간이 지나 삭제")
            deleted++
        }
        return deleted
    }

    @Transactional
    override fun fillMissingParents(owner: Access): List<Document> {
        Permissions.requireOwner(owner, "backfilling parent documents")
        val missing = DocumentTree.missingParents(documents.livePaths())
        val context = WriteContext(owner, ChangeSource.SYSTEM, null, null)
        return missing.flatMap { tree.fillFolder(context, it) }
    }

    private fun transition(
        context: WriteContext,
        document: Document,
        transition: Transition,
        now: Instant,
    ): DocumentWrite {
        val moved = transition.apply(pending.confirm(document), now) ?: return unchanged(document)
        val change =
            RevisionChange
                .of(
                    originOf(context, document.spaceId),
                    transition.summary,
                    emptyList(),
                    now,
                ).withKind(RevisionKind.TRANSITION)
        val saved = documents.update(moved, Revision.of(moved, change), amended = false)
        journal.confirmed(saved, change)
        journal.record(context, transition.kind, subjectOf(saved), "${saved.header.title}: ${transition.summary}")
        return DocumentWrite(saved, saved.revision, emptyList(), amended = false, unchanged = false)
    }

    // A write that changes nothing adds no revision.
    private fun write(
        context: WriteContext,
        document: Document,
        next: Content,
        mode: WriteMode,
    ): DocumentWrite {
        val now = journal.now()
        val latest = documents.pending(document.id)
        val typing = mode.groupsInput(context)
        val continues = typing && latest != null && pending.continues(latest, context.access.actor, now)
        val current = if (continues || latest == null) document else pending.confirm(document)
        if (next.blocks == current.blocks && next.header == current.header) return unchanged(current)
        val changes = BlockDiff.between(current.blocks, next.blocks)
        if (typing) return type(context, current, next, if (continues) latest else null)
        val revised = current.revise(next.header, next.blocks, now)
        val summary =
            context.summary ?: ChangeSummary.of(document.header, next.header, changes, BlockDiff.reordered(document.blocks, next.blocks))
        val change = RevisionChange.of(originOf(context, document.spaceId), summary, changes.map { it.blockId }, now).withKind(mode.kind)
        val saved = documents.update(revised, Revision.of(revised, change), amended = false)
        journal.confirmed(saved, change)
        journal.record(context, "document.revised", subjectOf(saved), "${saved.header.title}: ${change.summary}")
        return DocumentWrite(saved, saved.revision, change.changedBlocks, amended = false, unchanged = false)
    }

    private fun type(
        context: WriteContext,
        document: Document,
        next: Content,
        latest: Revision?,
    ): DocumentWrite {
        val now = journal.now()
        val edited = document.amend(next.header, next.blocks, now)
        val before = revisions.find(document.id, document.revision)
        val beforeBlocks = before?.content?.blocks.orEmpty()
        val changes = BlockDiff.between(beforeBlocks, next.blocks)
        val summary =
            context.summary ?: ChangeSummary.of(document.header, next.header, changes, BlockDiff.reordered(beforeBlocks, next.blocks))
        val incoming = RevisionChange.of(originOf(context, document.spaceId), summary, changes.map { it.blockId }, now)
        val change = latest?.change?.amendedWith(incoming, summary) ?: incoming
        val revision = Revision.of(edited, change).copy(number = document.revision + 1)
        val saved = documents.savePending(edited, revision)
        val subject = ActivitySubject(saved.spaceId.value, saved.id.value, null, null)
        journal.record(context, "document.revised", subject, "${saved.header.title}: ${change.summary}")
        return DocumentWrite(saved, saved.revision, change.changedBlocks, amended = latest != null, unchanged = false)
    }

    private fun requireVersion(
        document: Document,
        base: Int,
    ) {
        if (base != document.version) throw StaleRevisionException(base, document.version, document)
    }

    @Transactional
    override fun finalizeEdits(
        context: WriteContext,
        id: DocumentId,
        baseVersion: Int,
    ): DocumentWrite {
        journal.replayed(context, FINALIZE)?.let { return replay(context.access, it) }
        val document = lockForEdit(context.access, id)
        val edit = documents.pending(id)
        val owned =
            edit
                ?.change
                ?.origin
                ?.actor
                ?.isSameAuthorAs(context.access.actor) == true
        val confirmed = if (owned && edit.version == baseVersion) pending.confirm(document) else document
        return remember(context, FINALIZE, unchanged(confirmed))
    }

    @Transactional
    override fun finalizeExpiredEdits(): Int {
        val now = journal.now()
        val expired = documents.expiredPending(now)
        expired.forEach { id ->
            val document = documents.findByIdForUpdate(id) ?: return@forEach
            val edit = documents.pending(id) ?: return@forEach
            if (pending.expired(edit, now)) pending.confirm(document)
        }
        return expired.size
    }

    private fun headerWith(
        header: DocumentHeader,
        change: HeaderChange,
    ): DocumentHeader {
        val properties = header.properties
        val assignee = if (change.assignee == null) properties.assignee else change.assignee.login?.ifBlank { null }
        val next = DocumentProperties.of(change.tags ?: properties.tags, change.status ?: properties.status, assignee)
        return DocumentHeader(header.spaceId, change.path ?: header.path, change.title?.trim() ?: header.title, next, header.type)
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
        val space = if (kind == HEADER) spaces.findById(result.document.spaceId) else null
        val reference = space?.let { SpaceReference(it.id, it.slug, it.profile.name) } ?: result.space
        val snapshot = result.copy(marks = revisions.marks(result.document.id), space = reference)
        journal.remember(context, OperationRecord(kind, result.document.id.value, result.revision, documentWrite = snapshot))
        return snapshot
    }

    // Recheck current access, then replay only the captured result. Legacy keys never reapply writes.
    private fun replay(
        access: Access,
        record: OperationRecord,
    ): DocumentWrite = DocumentReplay(documents).result(access, record)

    private fun unchanged(document: Document): DocumentWrite =
        DocumentWrite(document, document.revision, emptyList(), amended = false, unchanged = true)

    private fun originOf(
        context: WriteContext,
        spaceId: SpaceId,
    ): ChangeOrigin {
        val references =
            if (context.codeReferences.isEmpty()) {
                emptyList()
            } else {
                val space = spaces.findById(spaceId) ?: throw DocumentNotFoundException(spaceId)
                CodeReferences.resolve(context.codeReferences, space.profile.links)
            }
        return ChangeOrigin(context.access.actor, context.source, references, context.importProvenance)
    }

    private fun subjectOf(document: Document): ActivitySubject =
        ActivitySubject(document.spaceId.value, document.id.value, null, document.revision)

    private data class Content(
        val header: DocumentHeader,
        val blocks: List<Block>,
    )

    private companion object {
        const val CREATE = "document.create"
        const val SAVE = "document.save"
        const val EDIT = "document.edit"
        const val HEADER = "document.header"
        const val LABEL = "revision.name"
        const val RESTORE_BLOCK = "document.restore_block"
        const val RESTORE = "document.restore"
        const val PURGE = "document.purge"
        const val FINALIZE = "document.finalize"
        val trashRetention: Duration = Duration.ofDays(30)
    }
}
