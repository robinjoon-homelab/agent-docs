package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import xyz.robinjoon.agentdocs.workspace.application.model.UploadedFile
import xyz.robinjoon.agentdocs.workspace.application.port.input.AssetUseCase
import xyz.robinjoon.agentdocs.workspace.domain.asset.Asset
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import java.net.URI
import java.security.Principal
import java.time.Duration
import java.util.UUID

@RestController
class AssetController(
    private val assets: AssetUseCase,
) {
    @PostMapping("/api/spaces/{slug}/assets", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @PathVariable slug: String,
        @RequestParam file: MultipartFile,
        principal: Principal?,
    ): ResponseEntity<Map<String, Any?>> {
        val uploaded = UploadedFile(file.originalFilename.orEmpty(), file.contentType, file.bytes)
        return created(assets.upload(CallerAccess.write(principal, null, null), SpaceSlug(slug), uploaded))
    }

    @PostMapping("/api/spaces/{slug}/assets/import")
    fun import(
        @PathVariable slug: String,
        @RequestBody request: ImportRequest,
        principal: Principal?,
    ): ResponseEntity<Map<String, Any?>> = created(assets.import(CallerAccess.write(principal, null, null), SpaceSlug(slug), request.url))

    // Files are immutable, so they cache for a year. The sandbox CSP and nosniff keep an SVG or a mislabeled file from
    // running script in this origin.
    @GetMapping("/api/assets/{id}/content")
    fun content(
        @PathVariable id: UUID,
        principal: Principal?,
    ): ResponseEntity<ByteArray> {
        val file = assets.content(CallerAccess.of(principal), AssetId(id))
        return ResponseEntity
            .ok()
            .contentType(MediaType.parseMediaType(file.asset.content.mediaType.mime))
            .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable())
            .header("Content-Security-Policy", "sandbox; default-src 'none'; img-src data:; style-src 'unsafe-inline'")
            .header("X-Content-Type-Options", "nosniff")
            .header("Content-Disposition", "inline")
            .body(file.bytes)
    }

    private fun created(asset: Asset): ResponseEntity<Map<String, Any?>> {
        val body =
            mapOf(
                "id" to asset.id.value,
                "mediaType" to asset.content.mediaType.mime,
                "size" to asset.content.size,
                "fileName" to asset.origin.fileName,
                "sourceUrl" to asset.origin.sourceUrl,
                "contentUrl" to "/api/assets/${asset.id}/content",
            )
        return ResponseEntity.created(URI.create("/api/assets/${asset.id}/content")).body(body)
    }
}

data class ImportRequest(
    val url: String,
)
