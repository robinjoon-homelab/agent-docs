package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.designmarkup

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.DesignJsonReader
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowDesignBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.design.DesignAddress
import java.nio.file.Path
import kotlin.io.path.readText

class FlowDesignAuthorMarkupTest {
    private val settings =
        FlowDesignBlock.parse(
            BlockId("settings"),
            """{"id":"settings","kind":"settings","modelVersion":4,"frame":"desktop","idPrefix":"F","nextScreen":1}""",
        )
    private val fixtures = Path.of("frontend/src/adapters/inbound/ui/pages/document/design/authoring/fixtures")

    @Test
    fun appendAllocatesIdsWithinAnExistingOwnerAndPreservesItsSiblings() {
        val initial =
            DesignMarkup(
                listOf(settings),
            ).create("<div data-key=\"root\"><p>Kept</p></div>", AuthorTarget("screen", "Home", "home", emptyList()))
        val parent = DesignAddress("home", "root")
        val target = AuthorTarget("layer", "", null, emptyList(), parent)
        val updated = DesignMarkup(initial).create("<p>Added</p>", target)
        assertThat(updated.last().id).isEqualTo(initial.last().id)
        assertThat(updated.last().json).contains("Kept", "Added")
        assertThat(MarkupInsertion.lastChild(updated.last(), parent)).isEqualTo("n2")
        assertThatThrownBy { DesignMarkup(initial).create("<p data-key=\"n1\">Duplicate</p>", target) }
            .isInstanceOf(InvalidRequestException::class.java)
    }

    @ParameterizedTest
    @ValueSource(strings = ["save-button", "new-document", "comment-card"])
    fun webAndApiImportTheSameEditableDesign(fixture: String) {
        val expected = objects(DesignJsonReader(fixtures.resolve("$fixture.expected.json").readText()).read()["blocks"])
        val screen = expected.single { it["kind"] == "screen" }
        val target = AuthorTarget("screen", screen["name"].toString(), screen.key(), emptyList())
        val result = DesignMarkup(listOf(settings)).create(fixtures.resolve("$fixture.txt").readText(), target)
        assertThat(result.map { it.kind }).containsExactlyElementsOf(expected.map { it["kind"].toString() })
        for ((actual, origin) in result.zip(expected)) {
            assertThat(actual.fields - "id").isEqualTo(origin - "id")
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "<div><script>alert(1)</script></div>",
            "<div><p style=\"background-image:url(https://example.invalid/a)\">x</p></div>",
            "<div><span onclick=\"go()\">x</span></div>",
            "<div><p>incomplete</div>",
            "<div><svg viewBox=\"0 0 1 1\"><path d=\"M0 0A1 1\"/></svg></div>",
            "<div><flow-use component=\"unknown\"></flow-use></div>",
        ],
    )
    fun invalidMarkupIsDiagnosedBeforeWriting(markup: String) {
        assertThatThrownBy { DesignMarkup(listOf(settings)).create(markup, AuthorTarget("screen", "Test", null, emptyList())) }
            .isInstanceOf(InvalidRequestException::class.java)
            .hasMessageContaining("line")
    }

    @Test
    fun standaloneComponentBindsTextAndRetainsEditableRotation() {
        val source = """<div><p data-bind="label"></p><svg viewBox="0 0 2 2"><path transform="rotate(90)" d="M0 0L1 1"/></svg></div>"""
        val result = DesignMarkup(listOf(settings)).create(source, AuthorTarget("component", "Icon", "icon", listOf("label")))
        assertThat(result.single { it.kind == "component" }.json).contains("localTransform", "matrix", "prop", "label")
        assertThat(result.first()).isEqualTo(settings)
    }
}
