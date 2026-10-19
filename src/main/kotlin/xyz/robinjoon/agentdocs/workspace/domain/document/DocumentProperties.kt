package xyz.robinjoon.agentdocs.workspace.domain.document

import java.util.Collections

// The labels people sort and filter by. The assignee is a GitHub login, the same name people and agents see, so
// the workspace never has to look a user up in another context.
@ConsistentCopyVisibility
data class DocumentProperties private constructor(
    val tags: List<String>,
    val status: DocumentStatus,
    val assignee: String?,
) {
    init {
        if (tags.size > MAX_TAGS || !tags.all(tagPattern::matches)) {
            throw InvalidDocumentException(
                "a document has at most $MAX_TAGS tags of 1 to 40 characters without spaces, commas, brackets, or quotes",
            )
        }
        if (assignee != null && !loginPattern.matches(assignee)) {
            throw InvalidDocumentException("assignee must be a GitHub login")
        }
    }

    companion object {
        const val MAX_TAGS = 20
        private val tagPattern = Regex("[^\\s,\\[\\]\"]{1,40}")
        private val loginPattern = Regex("[A-Za-z0-9][A-Za-z0-9-]{0,38}")
        val initial = DocumentProperties(emptyList(), DocumentStatus.DRAFT, null)

        fun of(
            tags: List<String>,
            status: DocumentStatus,
            assignee: String?,
        ): DocumentProperties =
            DocumentProperties(Collections.unmodifiableList(tags.map(String::trim).distinct()), status, assignee?.trim()?.removePrefix("@"))
    }
}
