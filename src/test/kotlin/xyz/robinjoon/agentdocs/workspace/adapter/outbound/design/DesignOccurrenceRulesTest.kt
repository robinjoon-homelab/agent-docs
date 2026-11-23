package xyz.robinjoon.agentdocs.workspace.adapter.outbound.design

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.agentdocs.workspace.domain.document.InvalidDocumentException

class DesignOccurrenceRulesTest {
    private val mapper = JsonMapper.builder().build()
    private val mount = """{"owner":"screen","node":"card"}"""
    private val inner = """{"owner":"Card","node":"inner","via":[{"owner":"screen","instance":"card","component":"Card"}]}"""
    private val rowA = """{"itemKey":"a","fields":{"status":"open"}}"""

    private fun document(
        changes: String = "",
        initialRows: String = rowA,
        binding: String = """{"prop":"gap"}""",
    ): JsonNode =
        mapper.readTree(
            """
            {"id":"doc","blocks":[
              {"id":"Card","kind":"component","props":[
                {"id":"title","type":"text"},{"id":"gap","type":"number","initial":8},{"id":"picture","type":"asset"},
                {"id":"show","type":"boolean"},{"id":"footer","type":"slot"},
                {"id":"state","type":"choice","choices":[{"id":"open"}]},
                {"id":"items","type":"list","fields":[{"id":"status","type":"choice","choices":[{"id":"open"}]}]}],
                "root":{"kind":"box","id":"inner","layout":{"mode":"column","gap":$binding},"style":{"overflow":{"x":"clip","y":"auto"}},
                  "children":[{"kind":"slot","id":"outlet","prop":"footer","fallback":[]}]}
              },
              {"id":"screen","kind":"screen","code":"M-01","root":{"id":"root","kind":"box","layout":{"mode":"column"},"children":[
                {"id":"card","kind":"instance","component":"Card","inputs":{"items":[$initialRows]},
                  "slots":{"footer":[{"id":"footer","kind":"box","layout":{"mode":"free"},"children":[{"id":"nested","kind":"text","text":"Nested"}]}]}}]}},
              {"id":"alternate","kind":"state","screen":"screen","changes":[$changes]}
            ]}
            """.trimIndent(),
        )

    @ParameterizedTest
    @ValueSource(
        strings = [
            """{"op":"input","prop":"state","value":"closed"}""",
            """{"op":"slot","prop":"state","assignment":{"children":[]}}""",
            """{"op":"itemSet","prop":"items","itemKey":"a","field":"status","value":"closed"}""",
            """{"op":"itemSet","prop":"items","itemKey":"a","field":"missing","value":"open"}""",
            """{"op":"itemRemove","prop":"items","itemKey":"missing"}""",
            """{"op":"itemInsert","prop":"items","item":{"itemKey":"a","fields":{}},"at":{"edge":"last"}}""",
            """{"op":"input","prop":"picture","value":"https://example.test/image"}""",
        ],
    )
    fun `rejects fresh changes that violate the receiving input declaration`(partial: String) {
        val withTarget = partial.dropLast(1) + ""","target":$mount}"""
        assertThatThrownBy { DesignOccurrenceRules.validate(document(withTarget), null) }.isInstanceOf(InvalidDocumentException::class.java)
    }

    @Test
    fun `permits an unchanged orphan and rejects a newly written change for the missing row`() {
        val original = """{"op":"itemSet","target":$mount,"prop":"items","itemKey":"a","field":"status","value":"open"}"""
        val previous = document(original)
        val orphan = document(original, "")
        val warning = DesignOccurrenceRules.validate(orphan, previous).single()
        assertThat(warning.designId).isEqualTo("alternate")
        assertThat(warning.code).isEqualTo("orphanedReference")
        assertThat(warning.detail).contains("unknown list item")
        val different = document("""{"op":"itemRemove","target":$mount,"prop":"items","itemKey":"a"}""", "")
        assertThatThrownBy { DesignOccurrenceRules.validate(different, previous) }.isInstanceOf(InvalidDocumentException::class.java)
    }

    @Test
    fun `validates layout bindings against the lexical declaration`() {
        assertThatCode { DesignOccurrenceRules.validate(document(), null) }.doesNotThrowAnyException()
        val invalid = document(binding = """{"prop":"title"}""")
        assertThatThrownBy { DesignOccurrenceRules.validate(invalid, null) }.isInstanceOf(InvalidDocumentException::class.java)
    }

    @Test
    fun `checks the physical parent after a component projects its slot value`() {
        val next = """{"owner":"screen","node":"footer"}"""
        val insert = """{"op":"insert","node":{"id":"new","kind":"text","text":"X"},"at":{"parent":$inner,"before":$next}}"""
        assertThatCode { DesignOccurrenceRules.validate(document(insert), null) }.doesNotThrowAnyException()
        assertThatThrownBy { DesignOccurrenceRules.validate(document(insert.replace("footer", "nested")), null) }
            .isInstanceOf(InvalidDocumentException::class.java)
    }

    @Test
    fun `only scrolls the axis owned by the projected box`() {
        val offset = """{"op":"set","target":$inner,"fields":{"scroll":{"y":"end"}}}"""
        assertThatCode { DesignOccurrenceRules.validate(document(offset), null) }.doesNotThrowAnyException()
        val wrongAxis = offset.replace(""""y":"end"""", """"x":"end"""")
        assertThatThrownBy { DesignOccurrenceRules.validate(document(wrongAxis), null) }.isInstanceOf(InvalidDocumentException::class.java)
    }

    @Test
    fun `rejects a well shaped patch whose target is a different node kind`() {
        val target = """{"owner":"screen","node":"nested"}"""
        val patch = """{"op":"set","target":$target,"fields":{"layout":{"mode":"free"}}}"""
        assertThatThrownBy { DesignOccurrenceRules.validate(document(patch), null) }.isInstanceOf(InvalidDocumentException::class.java)
    }

    @Test
    fun `checks required fields after an entire change batch`() {
        val remove = """{"op":"mask","target":$inner,"path":"layout.mode"}"""
        val repair = """{"op":"leaf","target":$inner,"path":"layout.mode","value":"row"}"""
        assertThatThrownBy { DesignOccurrenceRules.validate(document(remove), null) }.isInstanceOf(InvalidDocumentException::class.java)
        assertThatCode { DesignOccurrenceRules.validate(document("$remove,$repair"), null) }.doesNotThrowAnyException()
    }
}
