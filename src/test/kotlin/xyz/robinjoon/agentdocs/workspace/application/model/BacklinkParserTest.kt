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

    @Test
    fun `canonical app urls and relative document paths count only for this app origin`() {
        val second = DocumentId(UUID.fromString("22222222-2222-4222-8222-222222222222"))
        val third = DocumentId(UUID.fromString("33333333-3333-4333-8333-333333333333"))
        val fourth = DocumentId(UUID.fromString("44444444-4444-4444-8444-444444444444"))
        val other = DocumentId(UUID.fromString("99999999-9999-4999-8999-999999999999"))
        val body =
            """
            [canon](https://docs.example.com/s/team/d/$id#thread-1)
            [redirect](https://docs.example.com:443/d/$second?from=note)
            [rel-space](/s/old-slug/d/$third#block-para01)
            [rel-short](/d/$fourth)
            [foreign](https://other.example.com/s/team/d/$other)
            [proto-rel](//docs.example.com/d/$other)
            [history](https://docs.example.com/s/team/d/$other/history)
            """.trimIndent()
        val parsed =
            BacklinkParser.targets(
                MarkdownReader.readBlocks(body).map { it.block },
                appBaseUrl = "https://docs.example.com",
            )
        assertThat(parsed).containsExactlyInAnyOrder(id, second, third, fourth)
    }
}
