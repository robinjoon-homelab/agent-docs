package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import xyz.robinjoon.agentdocs.workspace.application.model.ExportAsset
import xyz.robinjoon.agentdocs.workspace.application.model.ExportDocument
import xyz.robinjoon.agentdocs.workspace.application.model.ExportSpaceUnavailableException
import xyz.robinjoon.agentdocs.workspace.application.model.WorkspaceExport
import xyz.robinjoon.agentdocs.workspace.application.port.output.ExportCatalog
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug

class ExposedExportCatalog : ExportCatalog {
    override fun snapshot(space: SpaceSlug?): WorkspaceExport {
        val spaces =
            SpacesTable.selectAll().let { query ->
                if (space == null) query.toList() else query.where { SpacesTable.slug eq space.value }.toList()
            }
        if (space != null && spaces.isEmpty()) throw ExportSpaceUnavailableException(space)
        return ExportRows(spaces).snapshot()
    }
}

private class ExportRows(
    private val spaces: List<ResultRow>,
) {
    private val ids = spaces.map { it[SpacesTable.id] }
    private val documents = DocumentsTable.selectAll().where { DocumentsTable.spaceId inList ids }.toList()
    private val documentIds = documents.map { it[DocumentsTable.id] }
    private val revisions = DocumentRevisionsTable.selectAll().where { DocumentRevisionsTable.documentId inList documentIds }.toList()
    private val threads = ThreadsTable.selectAll().where { ThreadsTable.documentId inList documentIds }.toList()
    private val threadIds = threads.map { it[ThreadsTable.id] }
    private val comments = ThreadCommentsTable.selectAll().where { ThreadCommentsTable.threadId inList threadIds }.toList()
    private val liveComments = comments.filter { it[ThreadCommentsTable.deletedAt] == null }.map { it[ThreadCommentsTable.id] }

    private val edits = CommentEditsTable.selectAll().where { CommentEditsTable.commentId inList liveComments }.toList()
    private val bindings =
        DocumentAssetBindingsTable
            .selectAll()
            .where { DocumentAssetBindingsTable.documentId inList documentIds }
            .toList()

    fun snapshot(): WorkspaceExport {
        val assets = assetRows()
        val records = primaryRecords() + metadataRecords() + mapOf("assets" to assets.map { record(AssetsTable, it) })
        val contents =
            documents.associate { row ->
                row[DocumentsTable.id].toString() to
                    ExportDocument(row[DocumentsTable.title], DocumentRows.blocksOf(row[DocumentsTable.body]))
            }
        return WorkspaceExport(
            records,
            contents,
            assets.map { ExportAsset(it[AssetsTable.id].toString(), it[AssetsTable.mediaType].extension, it[AssetsTable.sha256]) },
        )
    }

    private fun assetRows(): List<ResultRow> {
        val targets =
            bindings.associate { row ->
                (row[DocumentAssetBindingsTable.documentId] to row[DocumentAssetBindingsTable.referencedAssetId]) to
                    row[DocumentAssetBindingsTable.storedAssetId]
            }
        val referenced =
            DocumentAssetReferences
                .read(documentIds.map(::DocumentId).toSet())
                .flatMap { (document, references) -> references.map { targets[document.value to it.value] ?: it.value } }
                .toSet() + targets.values
        return AssetsTable.selectAll().filter { it[AssetsTable.spaceId] in ids || it[AssetsTable.id] in referenced }
    }

    private fun primaryRecords(): Map<String, List<Map<String, Any?>>> =
        mapOf(
            "spaces" to spaces.map { record(SpacesTable, it) },
            "documents" to documents.map { record(DocumentsTable, it) + body(it[DocumentsTable.body]) },
            "revisions" to revisions.map { record(DocumentRevisionsTable, it) + body(it[DocumentRevisionsTable.body]) },
            "threads" to threads.map { record(ThreadsTable, it) },
            "comments" to
                comments.map { row ->
                    record(ThreadCommentsTable, row) + if (row[ThreadCommentsTable.deletedAt] != null) mapOf("body" to null) else emptyMap()
                },
        )

    private fun metadataRecords(): Map<String, List<Map<String, Any?>>> =
        mapOf(
            "revisionNames" to rows(RevisionNamesTable) { RevisionNamesTable.documentId inList documentIds },
            "threadRevisions" to rows(ThreadRevisionsTable) { ThreadRevisionsTable.threadId inList threadIds },
            "commentEdits" to edits.map { record(CommentEditsTable, it) },
            "documentAssetBindings" to bindings.map { record(DocumentAssetBindingsTable, it) },
            "formerSlugs" to rows(SpaceFormerSlugsTable) { SpaceFormerSlugsTable.spaceId inList ids },
            "spaceLinks" to rows(SpaceLinksTable) { SpaceLinksTable.spaceId inList ids },
        )

    private fun rows(
        table: Table,
        predicate: () -> org.jetbrains.exposed.v1.core.Op<Boolean>,
    ): List<Map<String, Any?>> = table.selectAll().where { predicate() }.map { record(table, it) }

    private fun record(
        table: Table,
        row: ResultRow,
    ): Map<String, Any?> = table.columns.associate { column -> column.name to scalar(row[column]) }

    private fun scalar(value: Any?): Any? =
        when (value) {
            null, is Number, is Boolean, is String -> value
            else -> value.toString()
        }

    private fun body(markdown: String): Map<String, Any?> = mapOf("blocks" to DocumentRows.blocksOf(markdown).map(PortableBlocks::write))
}
