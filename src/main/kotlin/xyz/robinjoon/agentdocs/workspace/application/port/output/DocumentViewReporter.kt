package xyz.robinjoon.agentdocs.workspace.application.port.output

import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import java.time.LocalDate

// Remembers on which days a person opened a document. One record per person, document, and day.
interface DocumentViewReporter {
    fun record(
        document: DocumentId,
        login: String,
        day: LocalDate,
    )
}
