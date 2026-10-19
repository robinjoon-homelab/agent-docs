package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentHeader
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentLifecycle
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentProperties
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.MarkdownReader

// How a document maps to its row. Blocks are stored as annotated markdown, which reads back to the same blocks with
// the same block, row, and column ids.
internal object DocumentRows {
    fun write(
        statement: UpdateBuilder<*>,
        document: Document,
    ) {
        val header = document.header
        statement[DocumentsTable.spaceId] = header.spaceId.value
        statement[DocumentsTable.path] = header.path.value
        statement[DocumentsTable.activePath] = header.path.value.takeUnless { document.isTrashed }
        statement[DocumentsTable.title] = header.title
        statement[DocumentsTable.status] = header.properties.status
        statement[DocumentsTable.tags] = tagsOf(header.properties.tags)
        statement[DocumentsTable.assignee] = header.properties.assignee
        statement[DocumentsTable.body] = bodyOf(document.blocks)
        statement[DocumentsTable.searchText] =
            (listOf(header.title) + document.blocks.map { it.plainText() }).joinToString("\n").lowercase()
        statement[DocumentsTable.revision] = document.revision
        statement[DocumentsTable.updatedAt] = document.lifecycle.updatedAt.utc()
        statement[DocumentsTable.trashedAt] = document.lifecycle.trashedAt?.utc()
    }

    fun document(row: ResultRow): Document =
        Document.restore(DocumentId(row[DocumentsTable.id]), header(row), blocksOf(row[DocumentsTable.body]), lifecycle(row))

    fun header(row: ResultRow): DocumentHeader =
        DocumentHeader(
            SpaceId(row[DocumentsTable.spaceId]),
            DocumentPath(row[DocumentsTable.path]),
            row[DocumentsTable.title],
            DocumentProperties.of(tagsFrom(row[DocumentsTable.tags]), row[DocumentsTable.status], row[DocumentsTable.assignee]),
        )

    fun lifecycle(row: ResultRow): DocumentLifecycle =
        DocumentLifecycle(
            row[DocumentsTable.revision],
            row[DocumentsTable.createdAt].toInstant(),
            row[DocumentsTable.updatedAt].toInstant(),
            row[DocumentsTable.trashedAt]?.toInstant(),
        )

    fun bodyOf(blocks: List<Block>): String = BlockMarkdown.write(blocks, annotated = true)

    fun blocksOf(body: String): List<Block> = MarkdownReader.readBlocks(body).map { it.block }

    // " a b " so that a tag filter is a substring match on " a ".
    fun tagsOf(tags: List<String>): String = tags.joinToString(" ", " ", " ")

    fun tagsFrom(value: String): List<String> = value.split(' ').filter(String::isNotEmpty)
}
