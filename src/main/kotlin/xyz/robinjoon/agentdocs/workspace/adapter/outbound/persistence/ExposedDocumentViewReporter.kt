package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentViewReporter
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import java.time.LocalDate

class ExposedDocumentViewReporter : DocumentViewReporter {
    // The first open of the day is recorded; later opens find the row and add nothing.
    override fun record(
        document: DocumentId,
        login: String,
        day: LocalDate,
    ) {
        val viewer = login.lowercase()
        val seen =
            DocumentViewsTable
                .selectAll()
                .where {
                    (DocumentViewsTable.documentId eq document.value) and (DocumentViewsTable.viewerLogin eq viewer) and
                        (DocumentViewsTable.viewedOn eq day)
                }.any()
        if (seen) return
        DocumentViewsTable.insert {
            it[documentId] = document.value
            it[viewerLogin] = viewer
            it[viewedOn] = day
        }
    }
}
