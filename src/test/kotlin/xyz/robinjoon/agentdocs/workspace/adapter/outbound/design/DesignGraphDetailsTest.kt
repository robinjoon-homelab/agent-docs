package xyz.robinjoon.agentdocs.workspace.adapter.outbound.design

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import xyz.robinjoon.agentdocs.workspace.domain.document.InvalidDocumentException

class DesignGraphDetailsTest {
    private val mapper = JsonMapper.builder().build()
    private val shape = """{"id":"badge","kind":"shape","geometry":{"kind":"ellipse","center":{"x":4,"y":4},"radii":{"x":4,"y":4}}}"""
    private val paint = """{"kind":"linear","id":"ink","stops":[{"id":"a","at":0,"color":"#000"},{"id":"b","at":1,"color":"#fff"}]}"""
    private val fill = """{"collection":"fills","id":"ink"}"""
    private val stop = """{"collection":"stops","id":"middle"}"""

    private fun shapeWithFill(): ObjectNode {
        val node = mapper.readValue(shape, ObjectNode::class.java)
        apply(node, """{"op":"detailInsert","collection":"fills","item":$paint,"at":{"edge":"first"}}""")
        return node
    }

    private fun apply(
        node: ObjectNode,
        text: String,
    ) {
        DesignGraphDetails.apply(node, mapper.readTree(text)) { throw AssertionError(it) }
    }

    @Test
    fun `a stop inserted in this batch can be addressed by ID and moved or removed`() {
        val node = shapeWithFill()
        val middle = """{"id":"middle","at":0.7,"color":"#777"}"""
        val insert = """{"op":"detailInsert","parents":[$fill],"collection":"stops","at":{"before":"b"},"item":$middle}"""
        apply(node, insert)
        apply(node, """{"op":"detailSet","parts":[$fill,$stop],"field":"color","value":"#380"}""")
        val stops =
            node
                .path("style")
                .path("fills")
                .path(0)
                .path("stops")
        assertThat(stops.path(1).path("color").stringValue()).isEqualTo("#380")
        apply(node, """{"op":"detailMove","parts":[$fill,$stop],"at":{"after":"b"}}""")
        assertThat(stops.path(2).path("id").stringValue()).isEqualTo("middle")
        assertThatThrownBy { DesignTokenRules(null).validateFields(node) }.hasMessageContaining("ascending")
        apply(node, """{"op":"detailRemove","parts":[$fill,$stop]}""")
        assertThat(stops.size()).isEqualTo(2)
        DesignTokenRules(null).validateFields(node)
    }

    @Test
    fun `a missing ordering sibling leaves the entire projection unchanged`() {
        val node = mapper.readValue(shape, ObjectNode::class.java)
        val before = node.deepCopy()
        val change = mapper.readTree("""{"op":"detailInsert","collection":"fills","at":{"after":"retired"},"item":$paint}""")
        val missing = mutableListOf<String>()
        DesignGraphDetails.apply(node, change, missing::add)
        assertThat(missing).hasSize(1)
        assertThat(node).isEqualTo(before)
    }

    @Test
    fun `duplicate IDs and incompatible detail types reject the edit`() {
        val node = shapeWithFill()
        assertThatThrownBy { apply(node, """{"op":"detailInsert","collection":"fills","at":{"edge":"first"},"item":$paint}""") }
            .isInstanceOf(InvalidDocumentException::class.java)
            .hasMessageContaining("duplicate")
        assertThatThrownBy { apply(node, """{"op":"detailMask","parts":[{"collection":"contours","id":"outline"}],"field":"closed"}""") }
            .isInstanceOf(InvalidDocumentException::class.java)
            .hasMessageContaining("requires a vector")
    }

    @Test
    fun `a single owned stroke paint exposes its stops by stable ID`() {
        val node = mapper.readValue(shape, ObjectNode::class.java)
        node.withObjectProperty("style").withArrayProperty("strokes").add(mapper.readTree("""{"id":"edge","width":2,"paint":$paint}"""))
        val parts = """[{"collection":"strokes","id":"edge"},{"collection":"paint","id":"ink"},{"collection":"stops","id":"b"}]"""
        apply(node, """{"op":"detailSet","parts":$parts,"field":"color","value":"#acf"}""")
        assertThat(node.at("/style/strokes/0/paint/stops/1/color").stringValue()).isEqualTo("#acf")
    }
}
