package xyz.robinjoon.agentdocs.workspace.application

import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.now
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.owner
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.revision
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentRepository
import xyz.robinjoon.agentdocs.workspace.application.service.PendingEdits
import xyz.robinjoon.agentdocs.workspace.domain.Actor
import xyz.robinjoon.agentdocs.workspace.domain.document.ChangeSource
import xyz.robinjoon.agentdocs.workspace.domain.document.history.ChangeOrigin
import xyz.robinjoon.agentdocs.workspace.domain.document.history.ChangePeriod
import xyz.robinjoon.agentdocs.workspace.domain.document.history.RevisionChange
import java.util.UUID

class PendingEditsTest {
    private val pending =
        PendingEdits(mockk<DocumentRepository>(), mockk<xyz.robinjoon.agentdocs.workspace.application.service.WriteJournal>())
    private val edit = revision(2, emptyList(), ChangeOrigin(owner, ChangeSource.WEB), now)

    @Test
    fun `typing expires at exactly thirty seconds of inactivity`() {
        assertThat(pending.expired(edit, now.plusSeconds(29))).isFalse()
        assertThat(pending.expired(edit, now.plusSeconds(30))).isTrue()
        assertThat(pending.continues(edit, owner, now.plusSeconds(29))).isTrue()
        assertThat(pending.continues(edit, owner, now.plusSeconds(30))).isFalse()
    }

    @Test
    fun `continued activity cannot extend the revision beyond ten minutes`() {
        val change = edit.change
        val active =
            edit.copy(
                change =
                    RevisionChange.restore(
                        change.origin,
                        change.summary,
                        change.changedBlocks,
                        ChangePeriod(now, now.plusSeconds(590)),
                    ),
            )

        assertThat(pending.expired(active, now.plusSeconds(599))).isFalse()
        assertThat(pending.expired(active, now.plusSeconds(600))).isTrue()
        assertThat(pending.continues(active, owner, now.plusSeconds(600))).isFalse()
    }

    @Test
    fun `only the same author can continue an unexpired pending revision`() {
        val other = Actor.human(UUID.fromString("99999999-9999-4999-8999-999999999999"), "other")

        assertThat(pending.continues(edit, owner, now)).isTrue()
        assertThat(pending.continues(edit, other, now)).isFalse()
    }
}
