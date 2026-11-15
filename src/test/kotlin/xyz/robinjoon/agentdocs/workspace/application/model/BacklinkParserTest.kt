package xyz.robinjoon.agentdocs.workspace.application.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.MarkdownReader
import java.util.UUID

class BacklinkParserTest {
    private val id = DocumentId(UUID.fromString("11111111-1111-4111-8111-111111111111"))

    @Test
    fun `real links include nested labels and table cells while code escape images and bare destinations do not`() {
        val body =
            """
            [nested [label]](doc:$id)

            | source | destination |
            | --- | --- |
            | linked | [table](doc:$id) |
            """.trimIndent()
        assertThat(BacklinkParser.targets(MarkdownReader.readBlocks(body).map { it.block })).containsExactly(id)
        val excluded =
            """
            \[escaped](doc:$id) ` [code](doc:$id) ` ![image](doc:$id) doc:$id

            ```md
            [fenced](doc:$id)
            ```
            """.trimIndent()
        assertThat(BacklinkParser.targets(MarkdownReader.readBlocks(excluded).map { it.block })).isEmpty()
    }

    @Test
    fun `unclosed code is literal but matching backtick runs hide links`() {
        val body = "`` [hidden](doc:$id) ` still code `` [shown](<doc:$id>)"
        assertThat(BacklinkParser.targets(MarkdownReader.readBlocks(body).map { it.block })).containsExactly(id)
    }
}
