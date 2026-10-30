package xyz.robinjoon.agentdocs.workspace.application.service

import xyz.robinjoon.agentdocs.application.model.ActivitySubject
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentLocation
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.RelatedChange
import xyz.robinjoon.agentdocs.workspace.application.model.RelatedWrite
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentRepository
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentHeader
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentProperties
import xyz.robinjoon.agentdocs.workspace.domain.document.PathTakenException
import xyz.robinjoon.agentdocs.workspace.domain.document.history.ChangeOrigin
import xyz.robinjoon.agentdocs.workspace.domain.document.history.Revision
import xyz.robinjoon.agentdocs.workspace.domain.document.history.RevisionChange
import xyz.robinjoon.agentdocs.workspace.domain.document.history.RevisionKind
import java.time.Instant
import java.util.UUID

// Keeps the parent of every document outside the trash outside the trash too: the parent is whatever document holds
// the leading segments of the path. Writes that add, move, or restore a document fill in missing parents, and writes
// that move, trash, restore, or delete a document take the documents under it along. Each document stays its own
// root; the ones written here commit or roll back with the document the request names, so a request never leaves a
// child without its parent.
internal class DocumentTree(
    private val documents: DocumentRepository,
    private val journal: WriteJournal,
) {
    // Creates an empty document at each ancestor of the path that has none, top first, and locks those that exist.
    fun fillParents(
        context: WriteContext,
        space: SpaceId,
        path: DocumentPath,
    ): List<RelatedWrite> =
        missingAncestors(space, path).map { RelatedWrite(createParent(context, space, it, PARENT_CREATED), RelatedChange.CREATED) }

    // For a document coming back from the trash: a missing parent that is in the trash comes back with it, and only
    // a parent gone for good is created anew.
    fun reviveParents(
        context: WriteContext,
        space: SpaceId,
        path: DocumentPath,
        at: Instant,
    ): List<RelatedWrite> =
        missingAncestors(space, path).map { ancestor ->
            val trashed = documents.lockLastTrashedAt(space, ancestor)
            if (trashed == null) {
                RelatedWrite(createParent(context, space, ancestor, PARENT_CREATED), RelatedChange.CREATED)
            } else {
                RelatedWrite(transition(context, trashed, Along.PARENT_RESTORED, at), RelatedChange.RESTORED)
            }
        }

    // A folder path read before any lock gets documents only if, with its ancestors locked first as every other tree
    // write does, it still has none and something live still sits under it. Otherwise a trash move running meanwhile
    // could leave the new document without a parent.
    fun fillFolder(
        context: WriteContext,
        folder: DocumentLocation,
    ): List<Document> {
        val chain = folder.path.ancestors + folder.path
        val live = documents.lockLiveAt(folder.spaceId, chain).map { it.header.path }.toSet()
        if (folder.path in live || documents.lockLiveUnder(folder.spaceId, folder.path).isEmpty()) return emptyList()
        return chain.filter { it !in live }.map { createParent(context, folder.spaceId, it, FOLDER_TURNED_DOCUMENT) }
    }

    fun createParent(
        context: WriteContext,
        space: SpaceId,
        path: DocumentPath,
        summary: String,
    ): Document {
        val now = journal.now()
        val header = DocumentHeader(space, path, path.name, DocumentProperties.initial)
        val document = Document.create(DocumentId(UUID.randomUUID()), header, emptyList(), now)
        val change = RevisionChange.of(originOf(context), summary, emptyList(), now)
        val saved = documents.add(document, Revision.of(document, change))
        journal.record(context, "document.created", subjectOf(saved), "${saved.header.title}: $summary")
        return saved
    }

    // Where each document under the moving one lands, checked before anything is written: a landing path that breaks
    // the path limits or that another document holds refuses the whole move.
    fun planMove(
        document: Document,
        to: DocumentPath,
    ): List<Pair<Document, DocumentPath>> {
        // The live documents under a trashed document's old path belong to whatever took that path since.
        if (document.isTrashed) throw InvalidRequestException("a document in the trash cannot move", "restore it first")
        val from = document.header.path
        if (to.isWithin(from)) throw InvalidRequestException("$from cannot move under itself", "pick a path outside it")
        val moves = documents.lockLiveUnder(document.spaceId, from).map { it to it.header.path.movedFrom(from, to) }
        requireFree(document.spaceId, listOf(to) + moves.map { it.second }, moves.map { it.first.id }.toSet() + document.id)
        return moves
    }

    fun moveAlong(
        context: WriteContext,
        moves: List<Pair<Document, DocumentPath>>,
    ): List<RelatedWrite> =
        moves.map { (document, target) ->
            val now = journal.now()
            val revised = document.revise(document.header.copy(path = target), document.blocks, now)
            val change = RevisionChange.of(originOf(context), MOVED_ALONG, emptyList(), now)
            val saved = documents.update(revised, Revision.of(revised, change), amended = false)
            journal.record(context, "document.revised", subjectOf(saved), "${saved.header.title}: $MOVED_ALONG")
            RelatedWrite(saved, RelatedChange.MOVED)
        }

    // Every live document under the trashed one goes to the trash at the same instant, which is how a restore later
    // tells the documents trashed together from those trashed on their own.
    fun trashAlong(
        context: WriteContext,
        document: Document,
        at: Instant,
    ): List<RelatedWrite> =
        documents.lockLiveUnder(document.spaceId, document.header.path).map {
            RelatedWrite(transition(context, it, Along.TRASHED, at), RelatedChange.TRASHED)
        }

    // The documents trashed together with this one, after checking that each of them and this one can take its path
    // back.
    fun planRestore(document: Document): List<Document> {
        val together = trashedTogether(document)
        val restoring = listOf(document) + together
        requireFree(document.spaceId, restoring.map { it.header.path }, restoring.map { it.id }.toSet())
        return together
    }

    fun restoreAlong(
        context: WriteContext,
        together: List<Document>,
        at: Instant,
    ): List<RelatedWrite> =
        together.map {
            RelatedWrite(transition(context, it, Along.RESTORED, at), RelatedChange.RESTORED)
        }

    fun trashedTogether(document: Document): List<Document> =
        document.lifecycle.trashedAt
            ?.let { documents.lockTrashedTogether(document.spaceId, document.header.path, it) }
            .orEmpty()

    fun purgeAlong(
        context: WriteContext,
        together: List<Document>,
    ) {
        together.forEach {
            documents.delete(it.id)
            journal.record(context, "document.purged", subjectOf(it), "${it.header.title}: $PURGED_ALONG")
        }
    }

    private fun missingAncestors(
        space: SpaceId,
        path: DocumentPath,
    ): List<DocumentPath> {
        val ancestors = path.ancestors
        if (ancestors.isEmpty()) return emptyList()
        val existing = documents.lockLiveAt(space, ancestors).map { it.header.path }.toSet()
        return ancestors.filter { it !in existing }
    }

    private fun requireFree(
        space: SpaceId,
        paths: List<DocumentPath>,
        moving: Set<DocumentId>,
    ) {
        paths.forEach { path ->
            val holder = documents.findByPath(space, path)
            if (holder != null && holder.id !in moving) throw PathTakenException(path)
        }
    }

    private fun transition(
        context: WriteContext,
        document: Document,
        along: Along,
        at: Instant,
    ): Document {
        val moved = along.transition.apply(document, at) ?: return document
        val change = RevisionChange.of(originOf(context), along.summary, emptyList(), at).withKind(RevisionKind.TRANSITION)
        val saved = documents.update(moved, Revision.of(moved, change), amended = false)
        journal.record(context, along.transition.kind, subjectOf(saved), "${saved.header.title}: ${along.summary}")
        return saved
    }

    private fun originOf(context: WriteContext): ChangeOrigin = ChangeOrigin(context.access.actor, context.source)

    private fun subjectOf(document: Document): ActivitySubject =
        ActivitySubject(document.spaceId.value, document.id.value, null, document.revision)

    companion object {
        private const val FOLDER_TURNED_DOCUMENT = "폴더를 문서로 바꿈"
        private const val PARENT_CREATED = "하위 문서를 두려고 만듦"
        private const val MOVED_ALONG = "상위 문서와 함께 경로 변경"
        private const val PURGED_ALONG = "상위 문서와 함께 영구 삭제"

        // Folder paths that have no document yet: leading segments of a live document's path that no live document
        // holds. Sorted so a parent comes before its children.
        fun missingParents(live: List<DocumentLocation>): List<DocumentLocation> {
            val held = live.toSet()
            return live
                .flatMap { location -> location.path.ancestors.map { DocumentLocation(location.spaceId, it) } }
                .filter { it !in held }
                .distinct()
                .sortedBy { it.path.value }
        }
    }
}

// A trash move made for another document's sake, with the summary its revision gets.
private enum class Along(
    val transition: Transition,
    val summary: String,
) {
    TRASHED(Transition.TRASH, "상위 문서와 함께 휴지통으로 이동"),
    RESTORED(Transition.UNTRASH, "상위 문서와 함께 휴지통에서 복원"),
    PARENT_RESTORED(Transition.UNTRASH, "하위 문서와 함께 휴지통에서 복원"),
}
