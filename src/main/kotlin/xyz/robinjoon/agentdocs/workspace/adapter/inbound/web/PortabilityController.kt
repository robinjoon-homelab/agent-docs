package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import xyz.robinjoon.agentdocs.workspace.application.model.FolderFile
import xyz.robinjoon.agentdocs.workspace.application.model.FolderImportResult
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.port.input.ExportUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.FolderImportUseCase
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import java.security.Principal

@RestController
class PortabilityController(
    private val imports: FolderImportUseCase,
    private val exports: ExportUseCase,
) {
    @PostMapping("/api/spaces/{slug}/imports/folder", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun importFolder(
        @PathVariable slug: String,
        @ModelAttribute request: FolderUpload,
        principal: Principal?,
    ): FolderImportResult {
        if (request.paths.size != request.files.size) throw InvalidRequestException("paths and files must have the same size")
        val entries = request.paths.zip(request.files).map { (path, file) -> FolderFile(path, file.contentType, file.bytes) }
        return imports.importFolder(CallerAccess.write(principal, null, null), SpaceSlug(slug), request.tool, entries)
    }

    @GetMapping("/api/exports/workspace.zip")
    fun workspace(principal: Principal?): ResponseEntity<ByteArray> = response(exports.export(CallerAccess.of(principal), null))

    @GetMapping("/api/spaces/{slug}/export.zip")
    fun space(
        @PathVariable slug: String,
        principal: Principal?,
    ): ResponseEntity<ByteArray> = response(exports.export(CallerAccess.of(principal), SpaceSlug(slug)))

    private fun response(bytes: ByteArray): ResponseEntity<ByteArray> =
        ResponseEntity
            .ok()
            .contentType(MediaType.parseMediaType("application/zip"))
            .header("Content-Disposition", "attachment; filename=agent-docs.zip")
            .header("Cache-Control", "no-store")
            .header("X-Content-Type-Options", "nosniff")
            .body(bytes)
}

// Multipart filenames are ignored for provenance; paths are explicit selected-root-relative values.
data class FolderUpload(
    val tool: String,
    val paths: List<String>,
    val files: List<MultipartFile>,
)
