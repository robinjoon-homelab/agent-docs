package xyz.robinjoon.agentdocs.workspace.application

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentWrite
import xyz.robinjoon.agentdocs.workspace.application.model.OperationRecord
import xyz.robinjoon.agentdocs.workspace.application.model.RelatedChange
import xyz.robinjoon.agentdocs.workspace.application.model.RelatedWrite
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentRepository
import xyz.robinjoon.agentdocs.workspace.application.service.DocumentReplay
import xyz.robinjoon.agentdocs.workspace.domain.Permission
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import java.util.UUID

class DocumentReplayAccessTest {
    @Test
    fun `a related document moved outside the connection stops the original snapshot from leaking`() {
        val root = WorkspaceFixtures.document(emptyList(), 2)
        val child = Document.restore(DocumentId(UUID.randomUUID()), root.header, emptyList(), root.lifecycle)
        val snapshot = DocumentWrite(root, 2, emptyList(), false, false, related = listOf(RelatedWrite(child, RelatedChange.MOVED)))
        val stored = OperationRecord("document.header", root.id.value, 2, documentWrite = snapshot)
        val documents = mockk<DocumentRepository>()
        every { documents.findById(root.id) } returns root
        every { documents.findById(child.id) } returns
            child.transfer(
                WorkspaceFixtures.otherSpaceId,
                child.header.path,
                WorkspaceFixtures.now,
            )
        assertThatThrownBy { DocumentReplay(documents).result(WorkspaceFixtures.agentAccess(Permission.EDIT, setOf(root.spaceId)), stored) }
            .isInstanceOf(DocumentNotFoundException::class.java)
        assertThat(DocumentReplay(documents).result(WorkspaceFixtures.ownerAccess, stored)).isEqualTo(snapshot)
    }
}
