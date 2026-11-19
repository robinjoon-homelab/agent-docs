package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import xyz.robinjoon.agentdocs.access.application.model.UploadToken
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.UploadedFile
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.input.AssetUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.UploadGrantUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.UploadRejectedException
import xyz.robinjoon.agentdocs.workspace.domain.asset.Asset
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetMediaType
import xyz.robinjoon.agentdocs.workspace.domain.document.ChangeSource
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import java.net.URI
import java.security.Principal
import java.time.Duration
import java.util.UUID

@RestController
class AssetController(
    private val assets: AssetUseCase,
    private val uploads: UploadGrantUseCase,
) {
    @PostMapping("/api/spaces/{slug}/assets", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @PathVariable slug: String,
        @RequestParam file: MultipartFile,
        @RequestParam(required = false) opId: String?,
        principal: Principal?,
    ): ResponseEntity<Map<String, Any?>> {
        val uploaded = UploadedFile(file.originalFilename.orEmpty(), file.contentType, file.bytes)
        return created(assets.upload(CallerAccess.write(principal, opId, null), SpaceSlug(slug), uploaded).asset)
    }

    // The other end of the command docs_upload hands an agent. The token comes in a header because addresses end up in
    // access logs. An upload made this way is recorded like one made through docs_upload.
    @PostMapping(UploadToken.ENDPOINT, consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadWithToken(
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String,
        @RequestParam file: MultipartFile,
        principal: Principal?,
    ): ResponseEntity<Map<String, Any?>> {
        val secret = UploadToken.secretOf(authorization.substringAfter(' ').trim()) ?: throw UploadRejectedException()
        val context = WriteContext(CallerAccess.of(principal), ChangeSource.MCP, null, null)
        val uploaded = uploads.upload(context, secret, UploadedFile(file.originalFilename.orEmpty(), file.contentType, file.bytes))
        return ResponseEntity
            .created(URI.create("/api/assets/${uploaded.asset.id}/content"))
            .body(ImageUploads.resultOf(uploaded.asset, uploaded.alt))
    }

    @PostMapping("/api/spaces/{slug}/assets/import")
    fun import(
        @PathVariable slug: String,
        @RequestBody request: ImportRequest,
        principal: Principal?,
    ): ResponseEntity<Map<String, Any?>> =
        created(assets.import(CallerAccess.write(principal, request.opId, null), SpaceSlug(slug), request.url).asset)

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
            .header("Content-Security-Policy", ImageUploads.ATTACHMENT_POLICY)
            .header("X-Content-Type-Options", "nosniff")
            .header("Content-Disposition", if (file.asset.content.mediaType.isImage) "inline" else "attachment")
            .body(file.bytes)
    }

    @GetMapping("/api/assets/{id}/source")
    fun source(
        @PathVariable id: UUID,
        principal: Principal?,
    ): Map<String, Any?> {
        val file = assets.content(CallerAccess.of(principal), AssetId(id))
        if (file.asset.content.mediaType != AssetMediaType.HTML) {
            throw InvalidRequestException("only HTML assets have preview source")
        }
        return mapOf("assetId" to id, "source" to file.bytes.toString(Charsets.UTF_8))
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
    val opId: String? = null,
)
