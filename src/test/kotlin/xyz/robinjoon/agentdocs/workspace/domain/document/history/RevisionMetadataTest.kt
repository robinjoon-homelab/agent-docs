package xyz.robinjoon.agentdocs.workspace.domain.document.history

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.domain.document.InvalidDocumentException

class RevisionMetadataTest {
    @Test
    fun `names are independent labels with a hundred character limit and empty removal`() {
        assertThat(RevisionName.normalize("  리뷰 요청  ")).isEqualTo("리뷰 요청")
        assertThat(RevisionName.normalize(" ")).isNull()
        assertThat(RevisionName.normalize("가".repeat(100))).hasSize(100)
        assertThatThrownBy { RevisionName.normalize("가".repeat(101)) }.isInstanceOf(InvalidDocumentException::class.java)
        assertThatThrownBy { RevisionName.normalize("two\nlines") }.isInstanceOf(InvalidDocumentException::class.java)
    }

    @Test
    fun `code references produce GitHub links only from valid repository sha and pr values`() {
        val reference = CodeReference("owner/repo", "a".repeat(40), 12)
        assertThat(reference.commitUrl).isEqualTo("https://github.com/owner/repo/commit/${"a".repeat(40)}")
        assertThat(reference.pullRequestUrl).isEqualTo("https://github.com/owner/repo/pull/12")
        assertThatThrownBy {
            CodeReference(
                "evil.test/../../repo",
                "a".repeat(40),
                null,
            )
        }.isInstanceOf(InvalidDocumentException::class.java)
        assertThatThrownBy { CodeReference("owner/repo", "short", null) }.isInstanceOf(InvalidDocumentException::class.java)
        assertThatThrownBy { CodeReference("owner/repo", null, 0) }.isInstanceOf(InvalidDocumentException::class.java)
    }

    @Test
    fun `origin snapshots preserve the authenticated actor and copy caller reference collections`() {
        val actor = xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.agent
        val supplied = mutableListOf(CodeReference("owner/repo", null, 1))
        val provenance = ImportProvenance("obsidian", "notes/one.md")
        val origin = ChangeOrigin(actor, xyz.robinjoon.agentdocs.workspace.domain.document.ChangeSource.MCP, supplied, provenance)
        supplied.clear()
        assertThat(origin.actor).isEqualTo(actor)
        assertThat(origin.codeReferences).hasSize(1)
        assertThat(origin.importProvenance).isEqualTo(provenance)
        assertThatThrownBy { ImportProvenance("tool", "../outside.md") }.isInstanceOf(InvalidDocumentException::class.java)
    }
}
