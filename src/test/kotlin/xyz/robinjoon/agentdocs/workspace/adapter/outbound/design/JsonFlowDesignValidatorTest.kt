package xyz.robinjoon.agentdocs.workspace.adapter.outbound.design

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.agentdocs.workspace.domain.document.InvalidDocumentException
import java.util.zip.GZIPInputStream

class JsonFlowDesignValidatorTest {
    private val validator = JsonFlowDesignValidator(JsonMapper.builder().build())

    @Test
    fun `accepted unknown tokens carry their owning design block`() {
        val node = """{"kind":"text","id":"note","text":"메모","style":{"text":{"color":{"token":"gone"}}}}"""
        val warning = validator.validate(document(node)).single()
        assertThat(warning.designId).isEqualTo("screen")
        assertThat(warning.code).isEqualTo("missingToken")
        assertThat(warning.detail).isEqualTo("gone")
    }

    @ParameterizedTest
    @ValueSource(strings = ["view", "edit", "dialog"])
    fun `accepts the sources used to validate the sixteen design artboards`(fixture: String) {
        val stream = requireNotNull(javaClass.getResourceAsStream("/flow-design/$fixture.v4.json.gz"))
        val document = GZIPInputStream(stream).bufferedReader().use { it.readText() }

        assertThatCode { validator.validate(document) }.doesNotThrowAnyException()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            """{"kind":"text","id":"mark","text":"save","script":"unused"}""",
            """{"kind":"image","id":"mark","asset":"https://example.com/photo","alt":"photo"}""",
            """{"kind":"icon","id":"mark","icon":{"svgAsset":"data:image/svg+xml,invalid"}}""",
            """{"kind":"text","id":"mark","text":"save","style":{"fill":"url(https://example.com/photo)"}}""",
            """{"kind":"text","id":"mark","text":"save","style":{"text":{"font":{"local":"https://example.com/font"}}}}""",
        ],
    )
    fun `rejects unknown behavior and external resources at the offending design node`(node: String) {
        assertThatThrownBy { validator.validate(document(node)) }
            .isInstanceOf(InvalidDocumentException::class.java)
            .hasMessageContaining("/blocks/1/root/children/0")
            .hasMessageContaining("mark")
    }

    @Test
    fun `literal text can describe markup and links without becoming an executable resource`() {
        val inert = """{"kind":"text","id":"message","text":"<example> https://example.com/guide </example>"}"""

        assertThatCode { validator.validate(document(inert)) }.doesNotThrowAnyException()
    }

    @Test
    fun `rejects coordinates outside the finite rendering range`() {
        val node = """{"kind":"text","id":"mark","text":"hello","position":{"mode":"absolute","x":{"start":1e309},"y":{"start":0}}}"""
        assertThatThrownBy { validator.validate(document(node)) }
            .isInstanceOf(InvalidDocumentException::class.java)
            .hasMessageContaining("finite")
    }

    @Test
    fun `a different document cannot supply the history used to check orphaned references`() {
        val current = document("""{"kind":"text","id":"mark","text":"hello"}""")
        assertThatThrownBy { validator.validate(current, current.replace("\"doc\"", "\"other\"")) }
            .isInstanceOf(InvalidDocumentException::class.java)
            .hasMessageContaining("same document")
    }

    @Test
    fun `rejects duplicate JSON keys and trailing JSON values`() {
        val duplicate = """{"kind":"text","id":"a","id":"b","text":"text"}"""
        assertThatThrownBy { validator.validate(document(duplicate)) }.isInstanceOf(InvalidDocumentException::class.java)
        assertThatThrownBy { validator.validate(document(duplicate.replace(""""id":"a",""", "")) + " {}") }
            .isInstanceOf(InvalidDocumentException::class.java)
    }

    private fun document(node: String): String =
        """{"id":"doc","blocks":[{"kind":"settings","id":"cfg","modelVersion":4,"frame":"phone","idPrefix":"F","nextScreen":2},""" +
            """{"id":"screen","kind":"screen","code":"F-01","name":"screen","root":{"kind":"box","id":"root","layout":{"mode":"free"},"children":[$node]}}]}"""
}
