package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
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
            .header("Content-Security-Policy", "sandbox; default-src 'none'; img-src data:; style-src 'unsafe-inline'")
            .header("X-Content-Type-Options", "nosniff")
            .header("Content-Disposition", if (file.asset.content.mediaType.isImage) "inline" else "attachment")
            .body(file.bytes)
    }

    @GetMapping("/api/documents/{id}/assets/{assetId}/source")
    fun source(
        @PathVariable id: UUID,
        @PathVariable assetId: UUID,
        principal: Principal?,
    ): ResponseEntity<Map<String, Any?>> {
        val file = assets.content(CallerAccess.of(principal), DocumentId(id), AssetId(assetId))
        if (file.asset.content.mediaType != AssetMediaType.HTML) throw InvalidRequestException("only HTML assets have preview source")
        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noStore())
            .body(mapOf("assetId" to assetId, "source" to file.bytes.toString(Charsets.UTF_8)))
    }
}
