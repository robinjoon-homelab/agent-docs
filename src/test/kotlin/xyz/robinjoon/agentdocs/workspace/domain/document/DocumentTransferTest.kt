package xyz.robinjoon.agentdocs.workspace.domain.document

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.MarkdownAssetReferences

class DocumentTransferTest {
    @Test
    fun `transfer changes location and version while preserving identity blocks and metadata`() {
        val original = WorkspaceFixtures.document(listOf(WorkspaceFixtures.paragraph("block1", "Text")), 3)
        val moved = original.transfer(WorkspaceFixtures.otherSpaceId, DocumentPath("archive/note"), WorkspaceFixtures.now)
        assertThat(moved.id).isEqualTo(original.id)
        assertThat(moved.blocks).isEqualTo(original.blocks)
        assertThat(moved.header.title).isEqualTo(original.header.title)
        assertThat(moved.header.properties).isEqualTo(original.header.properties)
        assertThat(moved.spaceId).isEqualTo(WorkspaceFixtures.otherSpaceId)
        assertThat(moved.header.path).isEqualTo(DocumentPath("archive/note"))
        assertThat(moved.version).isEqualTo(4)
        assertThat(moved.revision).isEqualTo(4)
    }

    @Test
    fun `a trashed document cannot transfer`() {
        val trashed = WorkspaceFixtures.document(emptyList(), 3, WorkspaceFixtures.now)
        assertThatThrownBy { trashed.transfer(WorkspaceFixtures.otherSpaceId, DocumentPath("note"), WorkspaceFixtures.now) }
            .isInstanceOf(InvalidDocumentException::class.java)
    }

    @Test
    fun `comment markdown references exclude fenced code inline code and escaped links`() {
        val asset = "88888888-8888-4888-8888-888888888888"
        val other = "99999999-9999-4999-8999-999999999999"
        val markdown = "[file](asset:$asset) `[code](asset:$other)` \\[literal](asset:$other)\n\n```text\n[code](asset:$other)\n```"
        assertThat(MarkdownAssetReferences.of(markdown)).containsExactly(AssetId(java.util.UUID.fromString(asset)))
    }
}
