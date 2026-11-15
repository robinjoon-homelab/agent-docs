package xyz.robinjoon.agentdocs.workspace.domain.document

import java.time.Instant

// Content versions and confirmed revision numbers advance independently.
data class DocumentLifecycle(
    val revision: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val trashedAt: Instant?,
    val version: Int = revision,
) {
    init {
        if (revision < 1) {
            throw InvalidDocumentException("revision starts at 1")
        }
        if (version < revision) {
            throw InvalidDocumentException("content version cannot precede the confirmed revision")
        }
        if (updatedAt < createdAt) {
            throw InvalidDocumentException("a document cannot change before it was created")
        }
    }

    fun next(at: Instant): DocumentLifecycle = copy(revision = revision + 1, version = version + 1, updatedAt = maxOf(at, updatedAt))

    // Pending input changes content without rewriting confirmed history.
    fun touched(at: Instant): DocumentLifecycle = copy(version = version + 1, updatedAt = maxOf(at, updatedAt))

    fun confirmed(): DocumentLifecycle = copy(revision = revision + 1)

    companion object {
        fun started(at: Instant): DocumentLifecycle = DocumentLifecycle(1, at, at, null)
    }
}
