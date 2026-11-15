package xyz.robinjoon.agentdocs.workspace.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.application.model.ImportPaths
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException

class ImportPathsTest {
    @Test
    fun `relative images resolve inside the selected root`() {
        assertThat(ImportPaths.resolve("notes/readme.md", "../images/a.png")).isEqualTo("images/a.png")
        assertThat(ImportPaths.resolve("notes/readme.md", "./a.png")).isEqualTo("notes/a.png")
    }

    @Test
    fun `unsafe paths and root escape are rejected`() {
        listOf("/private/secret.md", "C:/secret.md", "../secret.md", "a/../secret.md", "a\\b.md", "a/%2e%2e/b.md").forEach {
            assertThat(ImportPaths.safe(it)).isFalse()
        }
        listOf("../a.png", "/private/a.png", "https://example.com/a.png", "%2e%2e/a.png").forEach {
            assertThatThrownBy { ImportPaths.resolve("readme.md", it) }.isInstanceOf(InvalidRequestException::class.java)
        }
    }
}
