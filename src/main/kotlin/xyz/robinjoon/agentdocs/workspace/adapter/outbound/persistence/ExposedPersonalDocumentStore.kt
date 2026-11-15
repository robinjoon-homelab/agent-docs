package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import xyz.robinjoon.agentdocs.workspace.application.model.PersonalDocument
import xyz.robinjoon.agentdocs.workspace.application.port.output.PersonalDocumentStore
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import java.time.Instant
import java.util.UUID

class ExposedPersonalDocumentStore : PersonalDocumentStore {
    override fun list(userId: UUID): List<PersonalDocument> =
        PersonalDocumentsTable.selectAll().where { PersonalDocumentsTable.userId eq userId }.map {
            PersonalDocument(
                DocumentId(it[PersonalDocumentsTable.documentId]),
                it[PersonalDocumentsTable.viewedAt]?.toInstant(),
                it[PersonalDocumentsTable.favoriteAt]?.toInstant(),
            )
        }

    override fun recordView(
        userId: UUID,
        documentId: DocumentId,
        at: Instant,
    ) {
        PersonalDocumentsTable.upsert(
            PersonalDocumentsTable.userId,
            PersonalDocumentsTable.documentId,
            onUpdateExclude = listOf(PersonalDocumentsTable.favoriteAt),
        ) {
            it[PersonalDocumentsTable.userId] = userId
            it[PersonalDocumentsTable.documentId] = documentId.value
            it[viewedAt] = at.utc()
        }
    }

    override fun favorite(
        userId: UUID,
        documentId: DocumentId,
        at: Instant?,
    ) {
        PersonalDocumentsTable.upsert(
            PersonalDocumentsTable.userId,
            PersonalDocumentsTable.documentId,
            onUpdateExclude = listOf(PersonalDocumentsTable.viewedAt),
        ) {
            it[PersonalDocumentsTable.userId] = userId
            it[PersonalDocumentsTable.documentId] = documentId.value
            it[favoriteAt] = at?.utc()
        }
    }
}
