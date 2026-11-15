package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.support.persistence.MigratedH2Database
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.at
import xyz.robinjoon.agentdocs.workspace.application.model.BlockMark
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentWrite
import xyz.robinjoon.agentdocs.workspace.application.model.OperationKey
import xyz.robinjoon.agentdocs.workspace.application.model.OperationRecord
import xyz.robinjoon.agentdocs.workspace.application.model.OperationResultState
import xyz.robinjoon.agentdocs.workspace.application.model.RelatedChange
import xyz.robinjoon.agentdocs.workspace.application.model.RelatedWrite
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceReference
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadContext
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadView
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import xyz.robinjoon.agentdocs.workspace.domain.thread.Anchor
import xyz.robinjoon.agentdocs.workspace.domain.thread.AnchorResolution
import xyz.robinjoon.agentdocs.workspace.domain.thread.Comment
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentThread
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadLocation
import java.util.UUID

class ExposedOperationStoreTest {
    private val database = MigratedH2Database()
    private val store = ExposedOperationStore()

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `an operation id is remembered per writer and cannot be stored twice`() {
        val record = OperationRecord("document.edit", UUID.randomUUID(), 3)
        database.inTransaction { store.save(OperationKey("writer-a", "op-1"), record, at) }

        assertThat(database.inTransaction { store.find(OperationKey("writer-a", "op-1")) }).isEqualTo(record)
        assertThat(database.inTransaction { store.find(OperationKey("writer-b", "op-1")) }).isNull()
        assertThatThrownBy { database.inTransaction { store.save(OperationKey("writer-a", "op-1"), record, at) } }
            .isInstanceOf(ExposedSQLException::class.java)
    }

    @Test
    fun `document snapshots preserve no op flags blocks lifecycle and related documents`() {
        val space = SpaceId(UUID.randomUUID())
        val document = PersistenceFixtures.document(space, "one", listOf(PersistenceFixtures.paragraph("block1", "Original")))
        val related = PersistenceFixtures.document(space, "two", emptyList())
        val versioned = Document.restore(document.id, document.header, document.blocks, document.lifecycle.copy(version = 9))
        val result =
            DocumentWrite(
                versioned,
                1,
                listOf(document.blocks.first().id),
                true,
                true,
                true,
                listOf(RelatedWrite(related, RelatedChange.CREATED)),
                marks = listOf(BlockMark(document.blocks.first().id, PersistenceFixtures.agent, 1, at)),
                space = SpaceReference(space, SpaceSlug("original-space"), "Original space"),
            )
        val record = OperationRecord("document.edit", document.id.value, 1, documentWrite = result)
        val key = OperationKey("writer", "doc")

        database.inTransaction { store.save(key, record, at) }

        assertThat(database.inTransaction { store.find(key) }).isEqualTo(record)
    }

    @Test
    fun `thread snapshots preserve comments edits resolution context and written comment`() {
        val document = PersistenceFixtures.document(SpaceId(UUID.randomUUID()), "one", emptyList())
        val comment = Comment.write(CommentId(UUID.randomUUID()), PersistenceFixtures.agent, "First", at).edit("Edited", at)
        val thread =
            CommentThread.open(
                ThreadId(UUID.randomUUID()),
                ThreadLocation(document.spaceId, document.id, Anchor.Document, 1),
                comment,
                at,
            )
        val context = ThreadContext(SpaceSlug("test"), document.header.path, "Original title", "Anchor", "Previous", "Next")
        val result = ThreadView(thread, AnchorResolution.Attached(null, null), context, comment.id)
        val record = OperationRecord("comment.edit", comment.id.value, null, threadView = result)
        val key = OperationKey("writer", "thread")

        database.inTransaction { store.save(key, record, at) }

        assertThat(database.inTransaction { store.find(key) }).isEqualTo(record)
    }

    @Test
    fun `snapshot codecs preserve every anchor form and detached resolution`() {
        val document = PersistenceFixtures.document(SpaceId(UUID.randomUUID()), "one", emptyList())
        val comment = Comment.write(CommentId(UUID.randomUUID()), PersistenceFixtures.person, "First", at)
        val anchors =
            listOf(
                Anchor.Document,
                Anchor.Whole(BlockId("block1"), "Quote"),
                Anchor.Text(BlockId("block1"), 0, 5, "Quote"),
                Anchor.Cell(BlockId("table1"), BlockId("row001"), BlockId("col001"), "Cell"),
            )
        anchors.forEach { anchor ->
            val thread =
                CommentThread.open(
                    ThreadId(UUID.randomUUID()),
                    ThreadLocation(document.spaceId, document.id, anchor, 1),
                    comment,
                    at,
                )
            val resolved = requireNotNull(thread.resolve(PersistenceFixtures.person, at)).linkRevision(1, at)
            val view = ThreadView(requireNotNull(resolved), AnchorResolution.Detached, null, comment.id)
            val record = OperationRecord("thread.change", thread.id.value, null, threadView = view)

            assertThat(OperationSnapshotCodec.decode(record.copy(threadView = null), requireNotNull(OperationSnapshotCodec.encode(record))))
                .isEqualTo(record)
        }
    }

    @Test
    fun `comment removal clears copies from every writer while preserving success evidence`() {
        val original = Comment.write(CommentId(UUID.randomUUID()), PersistenceFixtures.person, "Private original", at)
        val comment = original.edit("Private edit", at)
        val other = Comment.write(CommentId(UUID.randomUUID()), PersistenceFixtures.agent, "Other comment", at)
        val current = thread(listOf(comment, other))
        val earlierDelete = current.deleteComment(other.id, other.author, at)
        val records =
            mapOf(
                OperationKey("writer-a", "open") to threadRecord(current),
                OperationKey("writer-b", "edit") to threadRecord(current, "comment.edit", comment.id.value),
                OperationKey("writer-c", "delete") to threadRecord(earlierDelete, "comment.delete", other.id.value),
            )
        val unaffectedKey = OperationKey("writer-a", "unrelated")
        val unaffected = threadRecord(thread(listOf(other)))
        database.inTransaction {
            records.forEach { (key, record) -> store.save(key, record, at) }
            store.save(unaffectedKey, unaffected, at)
            store.removeCommentResults(comment.id)
        }

        records.forEach { (key, record) ->
            assertThat(database.inTransaction { store.find(key) })
                .isEqualTo(record.copy(threadView = null, state = OperationResultState.REMOVED))
            assertThat(payload(key)).isNull()
        }
        assertThat(database.inTransaction { store.find(unaffectedKey) }).isEqualTo(unaffected)
    }

    @Test
    fun `a new deletion snapshot contains no deleted history and is removed when another comment is deleted`() {
        val first = Comment.write(CommentId(UUID.randomUUID()), PersistenceFixtures.person, "Old secret", at).edit("New secret", at)
        val second = Comment.write(CommentId(UUID.randomUUID()), PersistenceFixtures.agent, "Another secret", at)
        val deleted = thread(listOf(first, second)).deleteComment(first.id, first.author, at)
        val record = threadRecord(deleted, "comment.delete", first.id.value)
        val key = OperationKey("writer", "delete")
        database.inTransaction { store.save(key, record, at) }

        val saved = database.inTransaction { store.find(key) }
        assertThat(saved?.state).isEqualTo(OperationResultState.AVAILABLE)
        val savedComment = saved?.threadView?.thread?.comment(first.id)
        assertThat(savedComment?.body).isEmpty()
        assertThat(savedComment?.history?.edits).isEmpty()
        assertThat(payload(key)).doesNotContain("Old secret", "New secret")

        database.inTransaction { store.removeCommentResults(first.id) }
        assertThat(database.inTransaction { store.find(key) }).isEqualTo(record)

        database.inTransaction { store.removeCommentResults(second.id) }

        assertThat(database.inTransaction { store.find(key) })
            .isEqualTo(record.copy(threadView = null, state = OperationResultState.REMOVED))
        assertThat(payload(key)).isNull()
    }

    @Test
    fun `document removal includes related document and thread snapshots and keeps completion evidence`() {
        val space = SpaceId(UUID.randomUUID())
        val document = PersistenceFixtures.document(space, "deleted", emptyList())
        val parent = PersistenceFixtures.document(space, "parent", emptyList())
        val write = DocumentWrite(document, 1, emptyList(), false, false)
        val related = DocumentWrite(parent, 1, emptyList(), false, false, related = listOf(RelatedWrite(document, RelatedChange.CREATED)))
        val comment = Comment.write(CommentId(UUID.randomUUID()), PersistenceFixtures.person, "Copied secret", at)
        val affected =
            mapOf(
                OperationKey("one", "direct") to OperationRecord("document.edit", document.id.value, 1, documentWrite = write),
                OperationKey("two", "related") to OperationRecord("document.edit", parent.id.value, 1, documentWrite = related),
                OperationKey("three", "thread") to threadRecord(thread(listOf(comment), document.id)),
            )
        val completed = OperationRecord("document.purge", document.id.value, null, state = OperationResultState.COMPLETED)
        val legacy = OperationRecord("document.purge", document.id.value, null)
        val completeKey = OperationKey("one", "completed")
        val legacyKey = OperationKey("one", "legacy")
        val unrelatedKey = OperationKey("two", "unrelated")
        val unrelated =
            OperationRecord(
                "document.edit",
                parent.id.value,
                1,
                documentWrite = DocumentWrite(parent, 1, emptyList(), false, false),
            )
        database.inTransaction {
            affected.forEach { (key, record) -> store.save(key, record, at) }
            store.save(completeKey, completed, at)
            store.save(legacyKey, legacy, at)
            store.save(unrelatedKey, unrelated, at)
            store.removeDocumentResults(listOf(document.id))
            store.removeDocumentResults(emptyList())
        }

        affected.forEach { (key, record) ->
            assertThat(database.inTransaction { store.find(key) })
                .isEqualTo(record.copy(documentWrite = null, threadView = null, state = OperationResultState.REMOVED))
            assertThat(payload(key)).isNull()
        }
        assertThat(database.inTransaction { store.find(completeKey) }).isEqualTo(completed)
        assertThat(database.inTransaction { store.find(legacyKey) }).isEqualTo(legacy)
        assertThat(database.inTransaction { store.find(unrelatedKey) }).isEqualTo(unrelated)
    }

    @Test
    fun `comment cleanup distinguishes copied history from an empty tombstone`() {
        val comment = CommentId(UUID.randomUUID())
        val history = """{"threadView":{"comments":[{"id":"${comment.value}","body":"","edits":[{"body":"Old secret"}]}]}}"""
        val tombstone = """{"threadView":{"comments":[{"id":"${comment.value}","body":"","edits":[]}]}}"""

        assertThat(OperationSnapshotCodec.containsComment(history, comment)).isTrue()
        assertThat(OperationSnapshotCodec.containsComment(tombstone, comment)).isFalse()
        assertThat(OperationSnapshotCodec.containsComment(history, CommentId(UUID.randomUUID()))).isFalse()
    }

    private fun thread(
        comments: List<Comment>,
        document: DocumentId = DocumentId(UUID.randomUUID()),
    ): CommentThread {
        val location = ThreadLocation(SpaceId(UUID.randomUUID()), document, Anchor.Document, 1)
        val opened = CommentThread.open(ThreadId(UUID.randomUUID()), location, comments.first(), at)
        return comments.drop(1).fold(opened) { current, comment -> current.reply(comment, at) }
    }

    private fun threadRecord(
        thread: CommentThread,
        kind: String = "thread.open",
        target: UUID = thread.id.value,
    ): OperationRecord = OperationRecord(kind, target, null, threadView = ThreadView(thread, AnchorResolution.Attached(null, null), null))

    private fun payload(key: OperationKey): String? =
        database.inTransaction {
            WriteOperationsTable
                .selectAll()
                .where { (WriteOperationsTable.writer eq key.writer) and (WriteOperationsTable.operationId eq key.operationId) }
                .single()[WriteOperationsTable.payload]
        }
}
