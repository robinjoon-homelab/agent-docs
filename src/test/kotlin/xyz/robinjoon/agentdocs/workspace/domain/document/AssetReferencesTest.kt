package xyz.robinjoon.agentdocs.workspace.domain.document

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import java.util.UUID

class AssetReferencesTest {
    @Test
    fun `extracts image and attachment references while excluding code and escaped links`() {
        val id = AssetId(UUID.fromString("88888888-8888-4888-8888-888888888888"))
        val other = "99999999-9999-4999-8999-999999999999"
        val blocks =
            listOf(
                ImageBlock(BlockId("image00001"), id, "시안", "mockup"),
                Paragraph(BlockId("para000001"), InlineText("[첨부](asset:$id) `[코드](asset:$other)` \\[문자](asset:$other)")),
                CodeBlock(BlockId("code000001"), "text", "[코드](asset:$other)"),
            )

        assertThat(AssetReferences.of(blocks)).containsExactly(id)
    }
}
