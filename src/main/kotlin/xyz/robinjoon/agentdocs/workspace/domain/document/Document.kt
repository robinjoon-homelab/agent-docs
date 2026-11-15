package xyz.robinjoon.agentdocs.workspace.domain.document

import xyz.robinjoon.agentdocs.domain.AggregateRoot
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import java.time.Instant
import java.util.Collections

// Content versions advance on changes; confirming pending input only advances the immutable history.
@AggregateRoot
@ConsistentCopyVisibility
data class Document private constructor(
    val id: DocumentId,
    val header: DocumentHeader,
    val blocks: List<Block>,
    val lifecycle: DocumentLifecycle,
) {
    init {
        if (blocks.size > MAX_BLOCKS) {
            throw InvalidDocumentException("a document has at most $MAX_BLOCKS blocks")
        }
        val repeated =
            blocks
                .groupingBy { it.id }
                .eachCount()
                .filterValues { it > 1 }
                .keys
        if (repeated.isNotEmpty()) {
            throw InvalidDocumentException("block ids must be unique: ${repeated.joinToString()}")
        }
    }

    val spaceId: SpaceId get() = header.spaceId

    val revision: Int get() = lifecycle.revision

    val version: Int get() = lifecycle.version

    fun confirmRevision(): Document = copy(lifecycle = lifecycle.confirmed())

    fun transfer(
        space: SpaceId,
        path: DocumentPath,
        at: Instant,
    ): Document {
        if (isTrashed) throw InvalidDocumentException("a document in the trash cannot move")
        return copy(header = header.copy(spaceId = space, path = path), lifecycle = lifecycle.next(at))
    }

    val isTrashed: Boolean get() = lifecycle.trashedAt != null

    fun block(id: BlockId): Block? = blocks.firstOrNull { it.id == id }

    // A new revision with the given blocks and header. The space never changes here: moving between spaces is a
    // separate operation with its own access checks.
    fun revise(
        header: DocumentHeader,
        blocks: List<Block>,
        at: Instant,
    ): Document = rebuild(header, blocks, lifecycle.next(at))

    // Advances content while a person's input awaits revision confirmation.
    fun amend(
        header: DocumentHeader,
        blocks: List<Block>,
        at: Instant,
    ): Document = rebuild(header, blocks, lifecycle.touched(at))

    // Returns null when the document is already in the trash, so callers write only a real change.
    fun trash(at: Instant): Document? = if (isTrashed) null else copy(lifecycle = lifecycle.next(at).copy(trashedAt = at))

    fun untrash(at: Instant): Document? = if (isTrashed) copy(lifecycle = lifecycle.next(at).copy(trashedAt = null)) else null

    private fun rebuild(
        header: DocumentHeader,
        blocks: List<Block>,
        lifecycle: DocumentLifecycle,
    ): Document {
        if (header.spaceId != spaceId) {
            throw InvalidDocumentException("a revision cannot move the document to another space")
        }
        return copy(header = header, blocks = snapshot(blocks), lifecycle = lifecycle)
    }

    companion object {
        const val MAX_BLOCKS = 5_000

        fun create(
            id: DocumentId,
            header: DocumentHeader,
            blocks: List<Block>,
            at: Instant,
        ): Document = Document(id, header, snapshot(blocks), DocumentLifecycle.started(at))

        fun restore(
            id: DocumentId,
            header: DocumentHeader,
            blocks: List<Block>,
            lifecycle: DocumentLifecycle,
        ): Document = Document(id, header, snapshot(blocks), lifecycle)

        private fun snapshot(blocks: List<Block>): List<Block> = Collections.unmodifiableList(blocks.toList())
    }
}
