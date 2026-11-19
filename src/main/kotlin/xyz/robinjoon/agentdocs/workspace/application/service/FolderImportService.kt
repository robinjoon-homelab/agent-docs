package xyz.robinjoon.agentdocs.workspace.application.service

import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentDraft
import xyz.robinjoon.agentdocs.workspace.application.model.FolderFile
import xyz.robinjoon.agentdocs.workspace.application.model.FolderImportResult
import xyz.robinjoon.agentdocs.workspace.application.model.ImportPaths
import xyz.robinjoon.agentdocs.workspace.application.model.ImportedFile
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.model.UploadedFile
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.input.AssetUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.FolderImportUseCase
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentProperties
import xyz.robinjoon.agentdocs.workspace.domain.document.PathTakenException
import xyz.robinjoon.agentdocs.workspace.domain.document.history.ImportProvenance
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.MarkdownReader
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

// Public use-case proxies own each upload/create transaction; the folder deliberately has no encompassing commit.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FolderImportService(
    private val assets: AssetUseCase,
    private val documents: DocumentUseCase,
) : FolderImportUseCase {
    override fun importFolder(
        context: WriteContext,
        space: SpaceSlug,
        tool: String,
        files: List<FolderFile>,
    ): FolderImportResult {
        ImportProvenance(tool, "source.md")
        val batch = FolderImportBatch(context, space, tool, files)
        val results =
            files
                .withIndex()
                .sortedBy { entry -> entry.value.path.count { it == '/' } }
                .map { it.index to batch.importFile(it.value, assets, documents) }
        return FolderImportResult(results.sortedBy { it.first }.map { it.second })
    }
}

private class FolderImportBatch(
    private val context: WriteContext,
    private val space: SpaceSlug,
    private val tool: String,
    files: List<FolderFile>,
) {
    private val duplicates =
        files
            .groupingBy { it.path }
            .eachCount()
            .filterValues { it > 1 }
            .keys
    private val available = files.filter { ImportPaths.safe(it.path) && it.path !in duplicates }.associateBy { it.path }
    private val uploaded = mutableMapOf<String, String>()

    fun importFile(
        file: FolderFile,
        assets: AssetUseCase,
        documents: DocumentUseCase,
    ): ImportedFile {
        if (!ImportPaths.safe(file.path)) return ImportedFile("[unsafe path]", "failed", code = "unsafe_path")
        if (file.path in duplicates) return ImportedFile(file.path, "failed", code = "duplicate_path")
        if (!file.path.endsWith(".md", ignoreCase = true)) return ImportedFile(file.path, "skipped", code = "attachment_input")
        return create(file, assets, documents)
    }

    private fun create(
        file: FolderFile,
        assets: AssetUseCase,
        documents: DocumentUseCase,
    ): ImportedFile =
        try {
            val path = DocumentPath(file.path.dropLast(3))
            val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            val markdown = decoder.decode(ByteBuffer.wrap(file.bytes)).toString()
            val rewritten = ImportMarkdown.rewrite(markdown) { upload(file, it, assets) }
            val origin = context.copy(operationId = null, importProvenance = ImportProvenance(tool, file.path))
            val draft = DocumentDraft(path, path.name, DocumentProperties.initial, MarkdownReader.readBlocks(rewritten).map { it.block })
            val written = documents.create(origin, space, draft)
            ImportedFile(
                file.path,
                "created",
                written.document.id.value
                    .toString(),
            )
        } catch (exception: IllegalArgumentException) {
            rejected(file, exception)
        } catch (exception: PathTakenException) {
            rejected(file, exception)
        } catch (exception: java.nio.charset.CharacterCodingException) {
            rejected(file, exception)
        } catch (exception: xyz.robinjoon.agentdocs.workspace.application.model.AssetStorageException) {
            rejected(file, exception)
        }

    private fun rejected(
        file: FolderFile,
        exception: Exception,
    ): ImportedFile {
        val code =
            when (exception) {
                is PathTakenException -> "path_taken"
                is java.nio.charset.CharacterCodingException -> "invalid_utf8"
                is xyz.robinjoon.agentdocs.workspace.application.model.AssetStorageException -> "file_io_failed"
                is xyz.robinjoon.agentdocs.workspace.domain.asset.InvalidAssetException -> "invalid_image"
                else -> exception.message?.takeIf { it in knownFailures } ?: "import_rejected"
            }
        val related = (exception as? InvalidRequestException)?.fix?.takeIf(ImportPaths::safe)
        return ImportedFile(file.path, "failed", code = code, relatedPath = related)
    }

    private fun upload(
        file: FolderFile,
        image: String,
        assets: AssetUseCase,
    ): String {
        val relative = ImportPaths.resolve(file.path, image)
        val attachment = available[relative] ?: throw InvalidRequestException("missing_image", relative)
        if (relative.substringAfterLast('.').lowercase() !in imageExtensions) throw InvalidRequestException("unsupported_image", relative)
        return uploaded.getOrPut(relative) {
            assets
                .upload(
                    context.copy(operationId = null),
                    space,
                    UploadedFile(relative.substringAfterLast('/'), attachment.mediaType, attachment.bytes),
                ).asset.id.value
                .toString()
        }
    }

    private companion object {
        val imageExtensions = setOf("png", "jpg", "jpeg", "gif", "webp", "svg")
        val knownFailures = setOf("unsafe_image_path", "missing_image", "unsupported_image", "unsupported_image_syntax")
    }
}
