package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import xyz.robinjoon.agentdocs.workspace.application.model.VerifiedScene
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentAssetUseCase
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetMediaType
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import java.security.Principal
import java.util.UUID

@RestController
class DocumentAssetController(
    private val assets: DocumentAssetUseCase,
) {
    @GetMapping("/api/documents/{id}/assets/{assetId}/content")
    fun content(
        @PathVariable id: UUID,
        @PathVariable assetId: UUID,
        principal: Principal?,
    ): ResponseEntity<ByteArray> {
        val file = assets.content(CallerAccess.of(principal), DocumentId(id), AssetId(assetId))
        return ResponseEntity
            .ok()
            .contentType(MediaType.parseMediaType(file.asset.content.mediaType.mime))
            .cacheControl(CacheControl.noStore())
            .header("Content-Security-Policy", ImageUploads.ATTACHMENT_POLICY)
            .header("X-Content-Type-Options", "nosniff")
            .header("Content-Disposition", if (file.asset.content.mediaType.isImage) "inline" else "attachment")
            .body(file.bytes)
    }

    // HTML keeps its original two fields; an SVG or a drawing scene also says what it is, and a scene lists its texts.
    @GetMapping("/api/documents/{id}/assets/{assetId}/source")
    fun source(
        @PathVariable id: UUID,
        @PathVariable assetId: UUID,
        principal: Principal?,
    ): ResponseEntity<Map<String, Any?>> {
        val source = assets.source(CallerAccess.of(principal), DocumentId(id), AssetId(assetId))
        val type = source.asset.content.mediaType
        val body =
            when {
                type == AssetMediaType.HTML -> mapOf("assetId" to assetId, "source" to source.text)
                source.scene == null -> mapOf("assetId" to assetId, "mediaType" to type.mime, "source" to source.text)
                else -> mapOf("assetId" to assetId, "mediaType" to type.mime, "source" to source.text, "texts" to textsOf(source.scene))
            }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body)
    }

    private fun textsOf(scene: VerifiedScene): List<Map<String, Any?>> =
        scene.texts.map { mapOf("elementId" to it.elementId, "text" to it.text, "containerId" to it.containerId) }
}
