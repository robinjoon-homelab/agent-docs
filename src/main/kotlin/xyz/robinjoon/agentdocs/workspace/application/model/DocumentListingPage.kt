package xyz.robinjoon.agentdocs.workspace.application.model

import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID

data class DocumentListingPage(
    val documents: List<DocumentListing>,
    val nextCursor: String?,
)

data class DocumentListPosition(
    val updatedAt: Instant,
    val id: UUID,
) {
    override fun toString(): String = "$updatedAt/$id"

    companion object {
        fun read(cursor: String): DocumentListPosition {
            val parts = cursor.split('/', limit = 2)
            if (parts.size != 2) throw InvalidRequestException("Use the next cursor from the previous page")
            return try {
                DocumentListPosition(Instant.parse(parts[0]), UUID.fromString(parts[1])).also {
                    require(it.toString() == cursor) { "Document cursor is not canonical" }
                }
            } catch (exception: DateTimeParseException) {
                throw InvalidRequestException("Use the next cursor from the previous page", cause = exception)
            } catch (exception: IllegalArgumentException) {
                throw InvalidRequestException("Use the next cursor from the previous page", cause = exception)
            }
        }
    }
}
