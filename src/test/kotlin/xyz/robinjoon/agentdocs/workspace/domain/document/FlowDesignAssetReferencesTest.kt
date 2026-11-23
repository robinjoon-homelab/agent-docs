package xyz.robinjoon.agentdocs.workspace.domain.document

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetMediaType
import java.util.UUID

class FlowDesignAssetReferencesTest {
    private val svg = AssetId(UUID.randomUUID())
    private val photo = AssetId(UUID.randomUUID())
    private val font = AssetId(UUID.randomUUID())
    private val text = AssetId(UUID.randomUUID())

    private fun block(
        id: String,
        json: String,
    ) = FlowDesignBlock.parse(BlockId(id), json)

    @Test
    fun `typed assets travel through nested properties and row changes while text stays text`() {
        val child =
            block(
                "child001",
                """{"kind":"component","id":"child","props":[{"id":"logo","type":"asset"}],
            "root":{"id":"logo","kind":"icon","icon":{"svgAsset":{"prop":"logo"}}}}""",
            )
        val parent =
            block(
                "parent01",
                """{"kind":"component","id":"parent","props":[{"id":"rows","type":"list","fields":[
            {"id":"file","type":"asset"},{"id":"svgAsset","type":"text"}]}],"root":{"kind":"repeat","id":"each","items":{"prop":"rows"},
            "template":[{"kind":"instance","id":"row","component":"child","inputs":{"logo":{"repeat":"each","field":"file"}}}]}}""",
            )
        val screen =
            block(
                "screen01",
                """{"kind":"screen","id":"home","root":{"kind":"instance","id":"list","component":"parent",
            "inputs":{"rows":[{"itemKey":"a","fields":{"file":"$svg","svgAsset":"$text"}}]}}}""",
            )
        val state =
            block(
                "state001",
                """{"kind":"state","id":"on","screen":"home","changes":[{"op":"itemSet","target":{
            "owner":"home","node":"list"},"prop":"rows","field":"file","itemKey":"a","value":"$photo"}]}""",
            )
        val blocks = listOf(screen, parent, state, child)
        assertThat(AssetReferences.of(blocks)).containsExactlyInAnyOrder(svg, photo)
        assertThat(
            AssetReferences.designTypes(blocks),
        ).containsEntry(svg, setOf(AssetMediaType.SVG)).containsEntry(photo, setOf(AssetMediaType.SVG))
    }

    @Test
    fun `only real inline links in a note description reference uploaded attachments`() {
        val note = block("screen01", """{"kind":"screen","id":"n","frame":"note","description":"[첨부](asset:$photo) `[코드](asset:$text)`"}""")
        assertThat(AssetReferences.designTypes(listOf(note))).containsOnlyKeys(photo)
    }

    @Test
    fun `image paint font fallback and component defaults retain their real media roles`() {
        val component =
            block(
                "comp0001",
                """{"kind":"component","id":"c","props":[{"id":"old","type":"asset","initial":"$svg"}],
            "root":{"kind":"box","id":"a","style":{"fills":[{"kind":"image","image":{"asset":"$photo"}}],
            "text":{"fallback":[{"local":"$text"},{"asset":"$font"}]}}}}""",
            )
        val types = AssetReferences.designTypes(listOf(component))
        assertThat(types.keys).containsExactlyInAnyOrder(photo, font, svg)
        assertThat(types.getValue(photo)).contains(AssetMediaType.PNG).doesNotContain(AssetMediaType.BINARY)
        assertThat(types.getValue(font)).containsExactly(AssetMediaType.BINARY)
        assertThatThrownBy {
            AssetReferences.of(
                listOf(block("screen01", """{"kind":"screen","id":"s","root":{"kind":"image","asset":"unuploaded-name"}}""")),
            )
        }.isInstanceOf(InvalidDocumentException::class.java)
    }
}
