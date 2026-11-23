package xyz.robinjoon.agentdocs.workspace.adapter.outbound.design

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.agentdocs.workspace.domain.document.InvalidDocumentException

class DesignGraphRulesTest {
    private val mapper = JsonMapper.builder().build()
    private val settings = """{"kind":"settings","id":"config","modelVersion":4,"frame":"phone","idPrefix":"S","nextScreen":2}"""

    private fun box(
        id: String,
        children: String = "",
    ): String = """{"kind":"box","id":"$id","layout":{"mode":"free"},"children":[$children]}"""

    private fun screen(
        id: String = "screen",
        children: String = "",
    ): String = """{"kind":"screen","id":"$id","code":"S-01","name":"S","root":${box("root", children)}}"""

    private fun component(
        id: String,
        child: String,
    ): String = """{"kind":"component","id":"$id","name":"C","props":[],"root":${box("root", child)}}"""

    private fun validate(vararg blocks: String) {
        val document = """{"id":"doc","blocks":[${blocks.joinToString(",")}]}"""
        DesignGraphRules.validate(mapper.readTree(document))
    }

    @Test
    fun `nested instances and independent node namespaces can use unresolved targets`() {
        val instance = """{"kind":"instance","id":"instance","component":"unresolved"}"""
        val rectangle = """{"kind":"shape","id":"rectangle","geometry":{"kind":"rect","origin":{"x":0,"y":0},"width":2,"height":4}}"""
        validate(settings, screen(children = instance), component("card", rectangle), component("banner", rectangle))
    }

    @Test
    fun `rejects a cycle through a slot fallback inside a mask`() {
        val toCard = """{"kind":"instance","id":"card","component":"card"}"""
        val toPanel = """{"kind":"instance","id":"panel","component":"panel"}"""
        val slot = """{"kind":"slot","id":"slot","prop":"slot","fallback":[$toCard]}"""
        val mask = """{"kind":"mask","id":"mask","mode":"alpha","source":${box("mask-source")},"children":[${box("inner", slot)}]}"""
        assertThatThrownBy { validate(settings, screen(), component("card", toPanel), component("panel", mask)) }
            .isInstanceOf(InvalidDocumentException::class.java)
            .hasMessageContaining("cyclic component")
    }

    @Test
    fun `inserted and slot assigned nodes share the state block namespace`() {
        val node = box("duplicate")
        val changes =
            """
            {"op":"insert","at":{"parent":{"owner":"screen","node":"root"},"edge":"last"},"node":$node},
            {"op":"slot","target":{"owner":"screen","node":"shell"},"prop":"footer","assignment":{"children":[$node]}}
            """.trimIndent()
        val state = """{"kind":"state","id":"alternate","screen":"screen","name":"alternate","changes":[$changes]}"""
        assertThatThrownBy { validate(settings, screen(), state) }.hasMessageContaining("duplicate id 'duplicate'")
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            """{"kind":"screen","id":"screen","code":"S-02","name":"duplicate block","frame":"note"}""",
            """{"kind":"screen","id":"another","code":"S-01","name":"duplicate code","frame":"note"}""",
            """{"kind":"state","id":"a","screen":"ghost","name":"unknown owner","changes":[]}""",
            """{"kind":"annotation","id":"a","screen":"screen","state":"ghost","target":{"owner":"screen","node":"root"},"name":"A","description":"x"}""",
        ],
    )
    fun `rejects invalid block identities and ownership`(block: String) {
        assertThatThrownBy { validate(settings, screen(), block) }.isInstanceOf(InvalidDocumentException::class.java)
    }

    @Test
    fun `rejects an instance pointing at a screen ID`() {
        val instance = """{"kind":"instance","id":"bad","component":"screen"}"""
        assertThatThrownBy { validate(settings, screen(children = instance)) }.hasMessageContaining("not a component")
    }

    @Test
    fun `aliases in different modes are independent but a cycle within one mode is invalid`() {
        val blue = """{"type":"color","id":"blue","name":"blue","value":"#38f","modes":[{"mode":"dark","value":{"token":"ink"}}]}"""
        val ink = """{"type":"color","id":"ink","name":"ink","value":{"token":"blue"},"modes":[{"mode":"dark","value":"#eee"}]}"""

        fun library(second: String) = """{"id":"library","kind":"tokens","modes":[{"id":"dark","name":"dark"}],"entries":[$blue,$second]}"""
        validate(settings, screen(), library(ink))
        assertThatThrownBy { validate(settings, screen(), library(ink.replace(""""#eee"""", """{"token":"blue"}"""))) }
            .hasMessageContaining("cyclic token in dark")
    }

    @Test
    fun `an alias keeps the token type`() {
        val tokens =
            """{"kind":"tokens","id":"t","entries":[{"type":"spacing","id":"gap","name":"gap","value":8},
              {"type":"color","id":"ink","name":"ink","value":{"token":"gap"}}]}"""
        assertThatThrownBy { validate(settings, screen(), tokens) }.hasMessageContaining("must have type 'color'")
    }
}
