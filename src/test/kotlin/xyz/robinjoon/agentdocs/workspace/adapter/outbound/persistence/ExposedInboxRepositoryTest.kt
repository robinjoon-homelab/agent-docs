package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.support.persistence.MigratedH2Database
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.agent
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.at
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.inbox.InboxItem
import xyz.robinjoon.agentdocs.workspace.domain.inbox.InboxItemId
import xyz.robinjoon.agentdocs.workspace.domain.inbox.InboxKind
import xyz.robinjoon.agentdocs.workspace.domain.inbox.InboxRecipient
import xyz.robinjoon.agentdocs.workspace.domain.inbox.Notice
import xyz.robinjoon.agentdocs.workspace.domain.inbox.NoticeTarget
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId
import java.util.UUID

class ExposedInboxRepositoryTest {
    private val database = MigratedH2Database()
    private val repository = ExposedInboxRepository()
    private val robin = UUID.fromString("11111111-1111-4111-8111-111111111111")

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `items round trip, list newest first, and count and mark only unread ones of the person who claimed them`() {
        val older = item("robin", 0)
        val newer = item("robin", 60)
        val someoneElses = item("octocat", 30)
        database.inTransaction { repository.addAll(listOf(older, newer, someoneElses)) }
        database.inTransaction { repository.update(requireNotNull(older.markRead(at.plusSeconds(90)))) }

        assertThat(database.inTransaction { repository.claim("robin", robin) }).isEqualTo(2)
        assertThat(database.inTransaction { repository.list(robin, false, 10) }.map { it.id }).containsExactly(newer.id, older.id)
        assertThat(database.inTransaction { repository.list(robin, true, 10) }.map { it.id }).containsExactly(newer.id)
        assertThat(database.inTransaction { repository.findById(newer.id) }?.recipient).isEqualTo(InboxRecipient("robin", robin))
        assertThat(database.inTransaction { repository.countUnread(robin) }).isEqualTo(1)
        assertThat(database.inTransaction { repository.markAllRead(robin, at.plusSeconds(120)) }).isEqualTo(1)
        assertThat(database.inTransaction { repository.countUnread(UUID.randomUUID()) }).isZero()
    }

    @Test
    fun `claimed items stay with the person after their login changes and new ones join them`() {
        val beforeRename = item("robin", 0)
        database.inTransaction { repository.addAll(listOf(beforeRename)) }
        database.inTransaction { repository.claim("robin", robin) }
        val afterRename = item("robin-renamed", 60)
        database.inTransaction { repository.addAll(listOf(afterRename)) }

        database.inTransaction { repository.claim("robin-renamed", robin) }

        assertThat(
            database.inTransaction { repository.list(robin, false, 10) }.map { it.id },
        ).containsExactly(afterRename.id, beforeRename.id)
    }

    private fun item(
        recipient: String,
        offset: Long,
    ): InboxItem {
        val target =
            NoticeTarget(
                SpaceId(UUID.randomUUID()),
                DocumentId(UUID.randomUUID()),
                ThreadId(UUID.randomUUID()),
                CommentId(UUID.randomUUID()),
            )
        return InboxItem.deliver(
            InboxItemId(UUID.randomUUID()),
            recipient,
            Notice.of(InboxKind.MENTION, target, agent, "Done, @robin"),
            at.plusSeconds(offset),
        )
    }
}
