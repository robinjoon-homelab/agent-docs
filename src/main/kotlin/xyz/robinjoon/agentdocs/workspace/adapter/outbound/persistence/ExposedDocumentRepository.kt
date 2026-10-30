package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentFilter
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentListing
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentLocation
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentRepository
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import xyz.robinjoon.agentdocs.workspace.domain.document.PathTakenException
import xyz.robinjoon.agentdocs.workspace.domain.document.history.Revision
import java.time.Instant

class ExposedDocumentRepository : DocumentRepository {
    // Ids are random UUIDs, so a unique violation means another live document holds the path.
    override fun add(
        document: Document,
        revision: Revision,
    ): Document {
        try {
            DocumentsTable.insert {
                it[id] = document.id.value
                it[createdAt] = document.lifecycle.createdAt.utc()
                DocumentRows.write(it, document)
            }
        } catch (exception: ExposedSQLException) {
            if (exception.isUniqueViolation()) throw PathTakenException(document.header.path, exception)
            throw exception
        }
        RevisionRows.insert(revision)
        writeMarks(document, revision)
        return selectById(document.id).single().let(DocumentRows::document)
    }

    override fun update(
        document: Document,
        revision: Revision,
        amended: Boolean,
    ): Document {
        try {
            val updated = DocumentsTable.update({ DocumentsTable.id eq document.id.value }) { DocumentRows.write(it, document) }
            check(updated == 1) { "Document to update does not exist: ${document.id}" }
        } catch (exception: ExposedSQLException) {
            if (exception.isUniqueViolation()) throw PathTakenException(document.header.path, exception)
            throw exception
        }
        if (amended) RevisionRows.replace(revision) else RevisionRows.insert(revision)
        writeMarks(document, revision)
        return selectById(document.id).single().let(DocumentRows::document)
    }

    override fun findById(id: DocumentId): Document? = selectById(id).singleOrNull()?.let(DocumentRows::document)

    override fun findByIds(ids: List<DocumentId>): List<Document> {
        if (ids.isEmpty()) return emptyList()
        val found = DocumentsTable.selectAll().where { DocumentsTable.id inList ids.map { it.value } }.map(DocumentRows::document)
        val byId = found.associateBy { it.id }
        return ids.mapNotNull(byId::get)
    }

    override fun findByIdForUpdate(id: DocumentId): Document? = selectById(id).forUpdate().singleOrNull()?.let(DocumentRows::document)

    override fun findByPath(
        space: SpaceId,
        path: DocumentPath,
    ): Document? =
        DocumentsTable
            .selectAll()
            .where { (DocumentsTable.spaceId eq space.value) and (DocumentsTable.activePath eq path.value) }
            .singleOrNull()
            ?.let(DocumentRows::document)

    override fun lockLiveAt(
        space: SpaceId,
        paths: List<DocumentPath>,
    ): List<Document> {
        if (paths.isEmpty()) return emptyList()
        return DocumentsTable
            .selectAll()
            .where { (DocumentsTable.spaceId eq space.value) and (DocumentsTable.activePath inList paths.map { it.value }) }
            .forUpdate()
            .map(DocumentRows::document)
    }

    override fun lockLiveUnder(
        space: SpaceId,
        path: DocumentPath,
    ): List<Document> =
        under(space, path)
            .andWhere { DocumentsTable.trashedAt.isNull() }
            .forUpdate()
            .map(DocumentRows::document)
            .sortedBy { it.header.path.value }

    override fun lockTrashedTogether(
        space: SpaceId,
        path: DocumentPath,
        trashedAt: Instant,
    ): List<Document> =
        under(space, path)
            .andWhere { DocumentsTable.trashedAt eq trashedAt.utc() }
            .forUpdate()
            .map(DocumentRows::document)
            .sortedBy { it.header.path.value }

    override fun lockLastTrashedAt(
        space: SpaceId,
        path: DocumentPath,
    ): Document? =
        DocumentsTable
            .selectAll()
            .where {
                (DocumentsTable.spaceId eq space.value) and (DocumentsTable.path eq path.value) and
                    DocumentsTable.trashedAt.isNotNull()
            }.orderBy(DocumentsTable.trashedAt to SortOrder.DESC)
            .limit(1)
            .forUpdate()
            .singleOrNull()
            ?.let(DocumentRows::document)

    override fun livePaths(): List<DocumentLocation> =
        DocumentsTable
            .select(DocumentsTable.spaceId, DocumentsTable.path)
            .where { DocumentsTable.trashedAt.isNull() }
            .map { DocumentLocation(SpaceId(it[DocumentsTable.spaceId]), DocumentPath(it[DocumentsTable.path])) }

    // Joined with the latest revision for who made it; the body is not read.
    override fun list(
        spaces: Set<SpaceId>,
        filter: DocumentFilter,
        limit: Int,
    ): List<DocumentListing> {
        if (spaces.isEmpty()) return emptyList()
        val latest =
            DocumentsTable.join(DocumentRevisionsTable, JoinType.INNER, additionalConstraint = {
                (DocumentsTable.id eq DocumentRevisionsTable.documentId) and (DocumentsTable.revision eq DocumentRevisionsTable.revision)
            })
        val columns = DocumentsTable.columns - DocumentsTable.body - DocumentsTable.searchText + DocumentRevisionsTable.actor.columns
        val query = latest.select(columns).where { DocumentsTable.spaceId inList spaces.map { it.value } }
        return filtered(query, filter)
            .orderBy(DocumentsTable.updatedAt to SortOrder.DESC)
            .limit(limit)
            .map {
                DocumentListing(
                    DocumentId(it[DocumentsTable.id]),
                    DocumentRows.header(it),
                    DocumentRows.lifecycle(it),
                    DocumentRevisionsTable.actor.read(it),
                )
            }
    }

    override fun trashedBefore(cutoff: Instant): List<DocumentId> =
        DocumentsTable
            .select(DocumentsTable.id)
            .where { DocumentsTable.trashedAt less cutoff.utc() }
            .map { DocumentId(it[DocumentsTable.id]) }

    // Threads, inbox items, and marks exist only for this document. Uploaded assets stay, since other revisions or
    // documents may show them.
    override fun delete(id: DocumentId) {
        val threads = ThreadsTable.select(ThreadsTable.id).where { ThreadsTable.documentId eq id.value }.map { it[ThreadsTable.id] }
        ThreadRows.deleteThreads(threads)
        InboxItemsTable.deleteWhere { InboxItemsTable.documentId eq id.value }
        DocumentViewsTable.deleteWhere { DocumentViewsTable.documentId eq id.value }
        DocumentBlockMarksTable.deleteWhere { DocumentBlockMarksTable.documentId eq id.value }
        DocumentRevisionsTable.deleteWhere { DocumentRevisionsTable.documentId eq id.value }
        DocumentsTable.deleteWhere { DocumentsTable.id eq id.value }
    }

    private fun filtered(
        query: Query,
        filter: DocumentFilter,
    ): Query {
        query.andWhere { if (filter.trashed) DocumentsTable.trashedAt.isNotNull() else DocumentsTable.trashedAt.isNull() }
        filter.status?.let { status -> query.andWhere { DocumentsTable.status eq status } }
        filter.tag?.let { tag -> query.andWhere { DocumentsTable.tags like LikePatterns.containing(" $tag ") } }
        filter.folder?.let { folder -> query.andWhere { DocumentsTable.path like LikePatterns.startingWith("${folder.trimEnd('/')}/") } }
        return query
    }

    // A mark records the latest change of a block; marks of blocks no longer in the document are dropped.
    private fun writeMarks(
        document: Document,
        revision: Revision,
    ) {
        val current = document.blocks.map { it.id.value }
        val changed =
            revision.change.changedBlocks
                .map { it.value }
                .filter { it in current }
        DocumentBlockMarksTable.deleteWhere {
            (documentId eq document.id.value) and ((blockId inList changed) or (blockId notInList current))
        }
        changed.forEach { block ->
            DocumentBlockMarksTable.insert {
                it[documentId] = document.id.value
                it[blockId] = block
                it[this.revision] = revision.number
                actor.write(it, revision.change.origin.actor)
                it[changedAt] =
                    revision.change.period.updatedAt
                        .utc()
            }
        }
    }

    // Paths starting with the path and a slash, so payments/retry is under payments and payments-old is not.
    private fun under(
        space: SpaceId,
        path: DocumentPath,
    ): Query =
        DocumentsTable
            .selectAll()
            .where { (DocumentsTable.spaceId eq space.value) and (DocumentsTable.path like LikePatterns.startingWith("$path/")) }

    private fun selectById(id: DocumentId): Query = DocumentsTable.selectAll().where { DocumentsTable.id eq id.value }
}
