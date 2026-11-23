package xyz.robinjoon.agentdocs.workspace.application.service

import xyz.robinjoon.agentdocs.workspace.application.model.DesignRenderInput
import xyz.robinjoon.agentdocs.workspace.application.model.DesignRenderResult
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.RenderSelection
import xyz.robinjoon.agentdocs.workspace.application.model.RenderUnavailableException
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentAssetUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentQueryUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.FlowDesignRenderUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.output.FlowDesignRenderer
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetMediaType
import xyz.robinjoon.agentdocs.workspace.domain.document.AssetReferences
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowDesignBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.design.DesignScene
import xyz.robinjoon.agentdocs.workspace.domain.document.design.renderBlocks
import java.util.Base64
import java.util.concurrent.Semaphore

class FlowDesignRenderService(
    private val queries: DocumentQueryUseCase,
    private val files: DocumentAssetUseCase,
    private val renderer: FlowDesignRenderer,
) : FlowDesignRenderUseCase {
    private val slots = Semaphore(2)

    override fun render(
        access: Access,
        document: DocumentId,
        selection: RenderSelection,
    ): DesignRenderResult {
        val doc = queries.open(access, document).document
        val blocks = doc.blocks.filterIsInstance<FlowDesignBlock>()
        if (blocks.size != doc.blocks.size || blocks.isEmpty()) throw InvalidRequestException("Render needs a v4 flow document")
        if (!slots.tryAcquire()) throw RenderUnavailableException("Both render workers are busy; retry shortly")
        try {
            val scene = DesignScene(blocks, selection.screen, selection.state)
            selection.target?.let { scene.resolve(it) }
            val scoped = scene.renderBlocks(blocks)
            val assets = resources(access, document, scoped)
            val result = renderer.render(DesignRenderInput(scoped, scene.screen.designId, scene.state?.designId, selection, assets))
            return result.copy(measurements = result.measurements + ("version" to doc.version))
        } finally {
            slots.release()
        }
    }

    private fun resources(
        access: Access,
        document: DocumentId,
        blocks: List<FlowDesignBlock>,
    ): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var total = 0L
        for (id in AssetReferences.designTypes(blocks).keys) {
            val file = files.content(access, document, id)
            val type = file.asset.content.mediaType
            if (!type.isImage && type != AssetMediaType.BINARY) continue
            total += file.bytes.size
            if (total > MAX_ASSET_BYTES) throw InvalidRequestException("Render attachment budget is 24 MiB")
            val encoded = Base64.getEncoder().encodeToString(file.bytes)
            result[id.value.toString()] = "data:" + type.mime + ";base64," + encoded
        }
        return result
    }

    private companion object {
        const val MAX_ASSET_BYTES = 24L * 1024 * 1024
    }
}
