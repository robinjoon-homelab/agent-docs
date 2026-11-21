package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.selectAll
import xyz.robinjoon.agentdocs.workspace.application.model.BacklinkParser
import xyz.robinjoon.agentdocs.workspace.application.model.NavigationDocument
import xyz.robinjoon.agentdocs.workspace.application.port.output.NavigationCatalog
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId

class ExposedNavigationCatalog(
    private val appBaseUrl: String? = null,
) : NavigationCatalog {
    override fun documents(spaces: Set<SpaceId>): List<NavigationDocument> = entries(spaces).map { it.first }

    override fun linkingTo(
        target: DocumentId,
        spaces: Set<SpaceId>,
    ): List<NavigationDocument> =
        entries(
            spaces,
        ).filter { target in BacklinkParser.targets(DocumentRows.blocksOf(it.second, it.first.type), appBaseUrl) }.map { it.first }

    private fun entries(spaces: Set<SpaceId>): List<Pair<NavigationDocument, String>> {
        if (spaces.isEmpty()) return emptyList()
        val names = SpacesTable.selectAll().where { SpacesTable.id inList spaces.map { it.value } }.associateBy { it[SpacesTable.id] }
        return DocumentsTable
            .selectAll()
            .where {
                (DocumentsTable.spaceId inList spaces.map { it.value }) and DocumentsTable.trashedAt.isNull()
            }.orderBy(DocumentsTable.id)
            .mapNotNull { row ->
                val space = names[row[DocumentsTable.spaceId]] ?: return@mapNotNull null
                NavigationDocument(
                    DocumentId(row[DocumentsTable.id]),
                    SpaceId(row[DocumentsTable.spaceId]),
                    space[SpacesTable.slug],
                    space[SpacesTable.name],
                    row[DocumentsTable.path],
                    row[DocumentsTable.title],
                    row[DocumentsTable.type],
                ) to row[DocumentsTable.body]
            }
    }
}
