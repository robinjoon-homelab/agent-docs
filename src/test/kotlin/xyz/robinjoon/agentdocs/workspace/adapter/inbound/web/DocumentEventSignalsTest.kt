package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentEventSnapshot
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentNotFoundException
import xyz.robinjoon.agentdocs.workspace.domain.Permission
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import java.util.UUID

class DocumentEventSignalsTest {
    private val snapshot = DocumentEventSnapshot(WebFixtures.spaceId, 4, 3, false, "threads")
    private val invalidate = DocumentEventSignals.Signal("invalidate", "{}")

    @Test
    fun `the first check only invalidates, since the page has just read the document and its permission`() {
        assertThat(DocumentEventSignals.after(null, null, snapshot, Permission.EDIT)).containsExactly(invalidate)
        assertThat(DocumentEventSignals.after(snapshot, Permission.EDIT, snapshot, Permission.EDIT)).isEmpty()
    }

    @Test
    fun `a narrowed permission is signalled first without touching the document, and a changed document follows`() {
        val comment = DocumentEventSignals.Signal("permission", """{"permission":"comment"}""")
        val changed = DocumentEventSnapshot(WebFixtures.spaceId, 5, 3, false, "threads")

        assertThat(DocumentEventSignals.after(snapshot, Permission.EDIT, snapshot, Permission.COMMENT)).containsExactly(comment)
        assertThat(DocumentEventSignals.after(snapshot, Permission.EDIT, changed, Permission.COMMENT)).containsExactly(comment, invalidate)
        assertThat(DocumentEventSignals.after(snapshot, Permission.COMMENT, snapshot, Permission.EDIT))
            .containsExactly(DocumentEventSignals.Signal("permission", """{"permission":"edit"}"""))
    }

    @Test
    fun `losing the document or the sign-in ends with a last signal that says only why`() {
        assertThat(DocumentEventSignals.ended(DocumentNotFoundException(DocumentId(UUID.randomUUID()))))
            .isEqualTo(DocumentEventSignals.Signal("revoked", """{"reason":"not_found"}"""))
        assertThat(DocumentEventSignals.ended(UnauthenticatedException()))
            .isEqualTo(DocumentEventSignals.Signal("revoked", """{"reason":"unauthenticated"}"""))
        assertThat(DocumentEventSignals.ended(IllegalStateException("database unavailable"))).isNull()
    }
}
