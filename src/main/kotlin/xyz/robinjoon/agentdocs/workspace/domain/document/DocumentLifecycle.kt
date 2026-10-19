package xyz.robinjoon.agentdocs.workspace.domain.document

import java.time.Instant

// The revision counts this document's own changes from 1, independent of every other document.
data class DocumentLifecycle(
    val revision: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val trashedAt: Instant?,
) {
    init {
        if (revision < 1) {
            throw InvalidDocumentException("revision starts at 1")
        }
        if (updatedAt < createdAt) {
            throw InvalidDocumentException("a document cannot change before it was created")
        }
    }

    fun next(at: Instant): DocumentLifecycle = copy(revision = revision + 1, updatedAt = maxOf(at, updatedAt))

    // Keeps the revision number for a human edit that continues the latest revision.
    fun touched(at: Instant): DocumentLifecycle = copy(updatedAt = maxOf(at, updatedAt))

    companion object {
        fun started(at: Instant): DocumentLifecycle = DocumentLifecycle(1, at, at, null)
    }
}
