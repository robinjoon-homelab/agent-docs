package xyz.robinjoon.agentdocs.workspace.application.service

import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.application.model.ActivitySubject
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentTransfer
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentWrite
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.OperationRecord
import xyz.robinjoon.agentdocs.workspace.application.model.RelatedChange
import xyz.robinjoon.agentdocs.workspace.application.model.RelatedWrite
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceReference
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentTransferUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.SpaceNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.StaleRevisionException
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.InboxRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.ThreadRepository
import xyz.robinjoon.agentdocs.workspace.domain.Permission
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import xyz.robinjoon.agentdocs.workspace.domain.document.history.ChangeOrigin
import xyz.robinjoon.agentdocs.workspace.domain.document.history.Revision
import xyz.robinjoon.agentdocs.workspace.domain.document.history.RevisionChange
import xyz.robinjoon.agentdocs.workspace.domain.space.Space
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug

@Transactional(readOnly = true)
class DocumentTransferService(
    private val documents: DocumentRepository,
    private val spaces: SpaceRepository,
    private val threads: ThreadRepository,
    private val inbox: InboxRepository,
    private val assets: DocumentTransferAssets,
    private val journal: WriteJournal,
) : DocumentTransferUseCase {
    private val tree = DocumentTree(documents, journal)

    @Transactional
    override fun transfer(
        context: WriteContext,
        id: DocumentId,
        request: DocumentTransfer,
    ): DocumentWrite {
        journal.replayed(context, KIND)?.let { return DocumentReplay(documents).result(context.access, it) }
        val document = source(context, id)
        val target = destination(context, request.space)
        if (document.isTrashed) throw InvalidRequestException("a document in the trash cannot move", "restore it first")
        if (document.version != request.baseVersion) throw StaleRevisionException(request.baseVersion, document.version, document)
        if (document.spaceId == target.id && document.header.path == request.path) {
            return remember(context, DocumentWrite(document, document.revision, emptyList(), false, true), target)
        }
        val plan = tree.planTransfer(document, target.id, request.path)
        return remember(context, relocate(context, plan, target), target)
    }

    private fun source(
        context: WriteContext,
        id: DocumentId,
    ): Document {
        val document = documents.findByIdForUpdate(id)?.takeIf { context.access.sees(it.spaceId) } ?: throw DocumentNotFoundException(id)
        Permissions.require(context.access, document.spaceId, Permission.EDIT, "moving documents")
        return document
    }

    private fun destination(
        context: WriteContext,
        slug: SpaceSlug,
    ): Space {
        val target = spaces.findBySlug(slug)?.takeIf { context.access.sees(it.id) } ?: throw SpaceNotFoundException(slug)
        Permissions.require(context.access, target.id, Permission.EDIT, "moving documents")
        return target
    }

    private fun relocate(
        context: WriteContext,
        plan: List<Pair<Document, DocumentPath>>,
        target: Space,
    ): DocumentWrite {
        val ids = plan.map { it.first.id }.toSet()
        threads.lockByDocuments(ids)
        val parents = tree.fillParents(context, target.id, plan.first().second)
        assets.move(plan.map { it.first }, target.id)
        val moved = plan.map { (current, path) -> move(context, current, target, path) }
        threads.moveDocuments(ids, target.id)
        inbox.moveDocuments(ids, target.id)
        val root = moved.first()
        return DocumentWrite(
            root,
            root.revision,
            emptyList(),
            false,
            false,
            related = parents + moved.drop(1).map { RelatedWrite(it, RelatedChange.MOVED) },
        )
    }

    private fun move(
        context: WriteContext,
        document: Document,
        target: Space,
        path: DocumentPath,
    ): Document {
        val confirmed = PendingEdits(documents, journal).confirm(document)
        val moved = confirmed.transfer(target.id, path, journal.now())
        val summary = "${document.spaceId}/${document.header.path}에서 ${target.id}/${path}로 이사"
        val change = RevisionChange.of(ChangeOrigin(context.access.actor, context.source), summary.take(200), emptyList(), journal.now())
        val saved = documents.update(moved, Revision.of(moved, change), false)
        journal.confirmed(saved, change)
        journal.record(
            context,
            "document.transferred",
            ActivitySubject(
                saved.spaceId.value,
                saved.id.value,
                null,
                saved.revision,
            ),
            change.summary,
        )
        return saved
    }

    private fun remember(
        context: WriteContext,
        result: DocumentWrite,
        target: Space,
    ): DocumentWrite {
        val captured = result.copy(space = SpaceReference(target.id, target.slug, target.profile.name))
        journal.remember(context, OperationRecord(KIND, captured.document.id.value, captured.revision, documentWrite = captured))
        return captured
    }

    private companion object {
        const val KIND = "document.transfer"
    }
}
