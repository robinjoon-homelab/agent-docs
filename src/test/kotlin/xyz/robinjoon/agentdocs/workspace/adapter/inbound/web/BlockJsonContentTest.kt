package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.CodeBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown

class BlockJsonContentTest {
    private val json = JsonMapper.builder().build()

    @Test
    fun `display math survives JSON while old code stays fenced`() {
        val body = json.readTree("""{"id":"math000001","type":"code","language":"math","code":"x^2","displayMath":true}""")
        val math = BlockJson.read(body)

        assertThat(BlockJson.write(math)["displayMath"]).isEqualTo(true)
        assertThat(BlockMarkdown.of(math)).isEqualTo("$$\nx^2\n$$")
        val oldCode = CodeBlock(BlockId("math000001"), "math", "x^2")
        assertThat(BlockJson.write(oldCode)).doesNotContainKey("displayMath")
        assertThat(BlockMarkdown.of(oldCode)).isEqualTo("```math\nx^2\n```")
    }
}
