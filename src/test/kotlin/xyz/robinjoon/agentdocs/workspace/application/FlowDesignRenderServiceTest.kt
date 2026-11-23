package xyz.robinjoon.agentdocs.workspace.application

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.application.model.AssetFile
import xyz.robinjoon.agentdocs.workspace.application.model.DesignRenderResult
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentView
import xyz.robinjoon.agentdocs.workspace.application.model.RenderSelection
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentAssetUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentQueryUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.output.FlowDesignRenderer
import xyz.robinjoon.agentdocs.workspace.application.service.FlowDesignRenderService
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetMediaType
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowDesignBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.InvalidDocumentException
import xyz.robinjoon.agentdocs.workspace.domain.document.design.DesignAddress
import java.util.UUID

class FlowDesignRenderServiceTest {
    private val reader = mockk<DocumentQueryUseCase>()
    private val files = mockk<DocumentAssetUseCase>()
    private val renderer = mockk<FlowDesignRenderer>()
    private val renders = FlowDesignRenderService(reader, files, renderer)
    private val access = mockk<Access>()
    private val document = DocumentId(UUID.randomUUID())
    private val screen =
        FlowDesignBlock.parse(
            BlockId("screen01"),
            """{"kind":"screen","id":"home","root":{"id":"body","kind":"box"}}""",
        )
    private val pointer = RenderSelection(screen.id, null, DesignAddress("home", "body"), "full", false)

    @Test
    fun renderKeepsModelAndServerIdentifiersSeparateAndIncludesReadVersion() {
        readable()
        every { renderer.render(match { it.screen == "home" && it.selection.target == pointer.target }) } returns
            DesignRenderResult(mapOf("diagnostics" to emptyList<String>()), null)
        assertThat(renders.render(access, document, pointer).measurements["version"]).isEqualTo(4)
    }

    @Test
    fun aSelectionOutsideTheScreenIsRejectedBeforeRendering() {
        readable()
        val unknown = pointer.copy(target = DesignAddress("other", "body"))
        assertThatThrownBy { renders.render(access, document, unknown) }.isInstanceOf(InvalidDocumentException::class.java)
    }

    @Test
    fun renderLoadsAnImageOnlyThroughItsDocumentAndCallerScope() {
        val id = AssetId(UUID.randomUUID())
        val root =
            mapOf(
                "id" to "body",
                "kind" to "box",
                "children" to listOf(mapOf("id" to "photo", "kind" to "image", "asset" to id.value.toString())),
            )
        readable(screen.withFields(screen.fields + ("root" to root)))
        val file = mockk<AssetFile>()
        every { file.asset.content.mediaType } returns AssetMediaType.PNG
        every { file.bytes } returns byteArrayOf(1, 2, 3)
        every { files.content(access, document, id) } returns file
        every { renderer.render(match { it.assets == mapOf(id.value.toString() to "data:image/png;base64,AQID") }) } returns
            DesignRenderResult(mapOf("imageLoaded" to true), null)
        assertThat(renders.render(access, document, pointer).measurements["imageLoaded"]).isEqualTo(true)
    }

    private fun readable(block: FlowDesignBlock = screen) {
        val view = mockk<DocumentView>()
        every { view.document.blocks } returns listOf(block)
        every { view.document.version } returns 4
        every { reader.open(access, document) } returns view
    }
}
