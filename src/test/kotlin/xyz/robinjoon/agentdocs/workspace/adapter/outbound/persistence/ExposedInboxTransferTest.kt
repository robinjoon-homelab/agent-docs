package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.support.persistence.MigratedH2Database
import xyz.robinjoon.agentdocs.workspace.domain.inbox.InboxItem
import xyz.robinjoon.agentdocs.workspace.domain.inbox.InboxItemId
import xyz.robinjoon.agentdocs.workspace.domain.inbox.InboxKind
import xyz.robinjoon.agentdocs.workspace.domain.inbox.Notice
import xyz.robinjoon.agentdocs.workspace.domain.inbox.NoticeTarget
import java.util.UUID

class ExposedInboxTransferTest {
    private val database = MigratedH2Database()
    private val fixture = TransferPersistenceFixtures(database)
    private val inbox = ExposedInboxRepository()

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `inbox ownership follows its document while delivery recipient and excerpt stay intact`() {
        val document = fixture.document(fixture.source, "one", "Text")
        val thread = fixture.comment(document, "Comment", null)
        val target = NoticeTarget(document.spaceId, document.id, thread.id, thread.comments.single().id)
        val item =
            InboxItem.deliver(
                InboxItemId(UUID.randomUUID()),
                "reader",
                Notice.of(InboxKind.REPLY, target, PersistenceFixtures.person, "Excerpt"),
                PersistenceFixtures.at,
            )
        database.inTransaction { inbox.addAll(listOf(item)) }
        database.inTransaction { inbox.moveDocuments(setOf(document.id), fixture.target.id) }
        val moved = database.inTransaction { inbox.findById(item.id) }
        assertThat(moved?.notice?.target).isEqualTo(target.copy(spaceId = fixture.target.id))
        assertThat(moved?.notice?.excerpt).isEqualTo(item.notice.excerpt)
        assertThat(moved?.recipient).isEqualTo(item.recipient)
        assertThat(moved?.delivery).isEqualTo(item.delivery)
    }
}
