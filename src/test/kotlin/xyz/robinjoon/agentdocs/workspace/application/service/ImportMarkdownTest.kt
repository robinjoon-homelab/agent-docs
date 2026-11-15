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
}
