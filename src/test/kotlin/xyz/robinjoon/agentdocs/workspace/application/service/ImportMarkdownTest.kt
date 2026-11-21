package xyz.robinjoon.agentdocs.workspace.application.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException

class ImportMarkdownTest {
    @Test
    fun `images become assets while fenced examples remain text`() {
        val source = "![diagram](images/a.png)\n```md\n![example](other.png)\n```"
        val actual = ImportMarkdown.rewrite(source) { "11111111-1111-1111-1111-111111111111" }
        assertThat(actual).isEqualTo("![diagram](asset:11111111-1111-1111-1111-111111111111)\n```md\n![example](other.png)\n```")
    }

    @Test
    fun `reference syntax reports unsupported input instead of losing images`() {
        assertThatThrownBy { ImportMarkdown.rewrite("![diagram][picture]") { "id" } }
            .isInstanceOf(InvalidRequestException::class.java)
    }

    @Test
    fun `exported reading markdown with escaped brackets in alt text rewrites cleanly`() {
        val source = """![배포 흐름 [초안\]](../assets/svg-1.svg "excalidraw ../assets/scene-1.excalidraw")"""
        val actual = ImportMarkdown.rewrite(source) { "11111111-1111-4111-8111-111111111111" }
        assertThat(actual).isEqualTo(
            """![배포 흐름 [초안\]](asset:11111111-1111-4111-8111-111111111111 "excalidraw ../assets/scene-1.excalidraw")""",
        )
    }

    @Test
    fun `flow text is preserved instead of rewriting image shaped labels`() {
        val source = "---\ntype: flow\n---\n\n## flowSettings\n- frame: phone\n\n### label · text\n- value: ![literal](asset:id)"
        assertThat(ImportMarkdown.rewrite(source) { "rewritten" }).isEqualTo(source)
    }
}
