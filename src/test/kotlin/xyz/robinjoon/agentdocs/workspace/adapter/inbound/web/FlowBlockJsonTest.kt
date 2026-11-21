package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowRules

class FlowBlockJsonTest {
    private val json = JsonMapper.builder().build()

    @Test
    fun `flow settings and screens preserve null custom dimensions`() {
        FlowRules.initial("phoneSmall", "").forEach { block ->
            val encoded = json.readTree(json.writeValueAsString(BlockJson.write(block)))
            assertThat(BlockJson.read(encoded)).isEqualTo(block)
        }
    }

    @Test
    fun `unknown frame and fractional dimensions fail`() {
        assertThatThrownBy {
            BlockJson.read(json.readTree("""{"id":"settings1","type":"flowSettings","frame":"custom","idPrefix":"M","nextScreen":1}"""))
        }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy {
            BlockJson.read(json.readTree("""{"id":"settings1","type":"flowSettings","frame":"phone","idPrefix":"M","nextScreen":1.5}"""))
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `element JSON round trips every field including actions and an empty date`() {
        val input =
            json.readTree(
                """{"id":"element1","type":"flowElement","kind":"path","label":"날짜","value":"값","items":null,"date":"",
                "look":null,"x":1,"y":1000,"w":300,"h":40,"pinned":true,"group":"g1","annotated":false,"desc":"설명",
                "actions":[{"when":"완료","to":"M-02","mode":"sheet"}],"states":["기본",""]}""",
            )
        val block = BlockJson.read(input)
        val output = BlockJson.write(block)
        assertThat(output["date"]).isEqualTo("")
        assertThat(output["items"]).isNull()
        assertThat(output["actions"]).isEqualTo(listOf(mapOf("when" to "완료", "to" to "M-02", "mode" to "sheet")))
        assertThat(output["states"]).isEqualTo(listOf("기본", ""))
        assertThat(BlockJson.read(json.readTree(json.writeValueAsString(output)))).isEqualTo(block)
    }
}
