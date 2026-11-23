package xyz.robinjoon.agentdocs.workspace.application

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.application.port.output.ActivityReporter
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.context
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.documentId
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.header
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.now
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.ownerAccess
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.port.input.PermissionDeniedException
import xyz.robinjoon.agentdocs.workspace.application.port.input.StaleRevisionException
import xyz.robinjoon.agentdocs.workspace.application.port.output.DocumentRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.FlowDesignValidator
import xyz.robinjoon.agentdocs.workspace.application.port.output.RevisionCatalog
import xyz.robinjoon.agentdocs.workspace.application.service.DocumentService
import xyz.robinjoon.agentdocs.workspace.application.service.WriteJournal
import xyz.robinjoon.agentdocs.workspace.domain.Permission
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowDesignBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowRules
import xyz.robinjoon.agentdocs.workspace.domain.document.LegacyFlowUpgrade

class FlowUpgradeServiceTest {
    private val documents = mockk<DocumentRepository>()
    private val revisions = mockk<RevisionCatalog>()
    private val validator = mockk<FlowDesignValidator>()
    private val activity = mockk<ActivityReporter>()
    private val journal = WriteJournal(activity, mockk(), WorkspaceFixtures.clock)
    private val service = DocumentService(documents, revisions, mockk(), mockk(), journal, validator)
    private val old = Document.create(documentId, header.copy(type = "flow"), FlowRules.initial("desktop", "M"), now)

    @Test
    fun `upgrade publishes a confirmed revision and retry preserves subsequent writes`() {
        val expected = LegacyFlowUpgrade.convert(old.blocks)
        every { documents.findByIdForUpdate(documentId) } returns old
        every { documents.pending(documentId) } returns null
        every { validator.validate(FlowDesignBlock.documentJson(documentId, expected), null) } returns emptyList()
        every { documents.update(any(), any(), false) } answers { firstArg() }
        every { activity.record(any()) } just runs
        every { revisions.marks(documentId) } returns emptyList()
        val upgraded = service.upgradeFlow(context(ownerAccess), documentId, old.version)
        assertThat(upgraded.document.blocks).isEqualTo(expected)
        assertThat(upgraded.revision).isEqualTo(old.revision + 1)
        assertThat(upgraded.document.version).isEqualTo(old.version + 1)
        assertThat(upgraded.unchanged).isFalse()

        val newer = upgraded.document.revise(upgraded.document.header.copy(title = "사람이 고친 제목"), expected, now)
        every { documents.findByIdForUpdate(documentId) } returns newer
        val retry = service.upgradeFlow(context(ownerAccess), documentId, old.version)
        assertThat(retry.unchanged).isTrue()
        assertThat(retry.document).isEqualTo(newer)
        assertThat(retry.changedBlocks).isEmpty()
    }

    @Test
    fun `only an editor of the current legacy version can upgrade`() {
        every { documents.findByIdForUpdate(documentId) } returns old
        assertThatThrownBy { service.upgradeFlow(context(ownerAccess), documentId, old.version - 1) }
            .isInstanceOf(StaleRevisionException::class.java)
        val reader = WorkspaceFixtures.memberAccess(mapOf(old.spaceId to Permission.READ))
        assertThatThrownBy { service.upgradeFlow(context(reader), documentId, old.version) }
            .isInstanceOf(PermissionDeniedException::class.java)
    }

    @Test
    fun `ordinary documents cannot be upgraded`() {
        val ordinary = Document.create(documentId, header, emptyList(), now)
        every { documents.findByIdForUpdate(documentId) } returns ordinary
        assertThatThrownBy { service.upgradeFlow(context(ownerAccess), documentId, ordinary.version) }
            .isInstanceOf(InvalidRequestException::class.java)
    }
}
