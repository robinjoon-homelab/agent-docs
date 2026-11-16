package xyz.robinjoon.agentdocs.workspace.application.service

import xyz.robinjoon.agentdocs.workspace.application.model.UploadedFile
import xyz.robinjoon.agentdocs.workspace.application.model.VerifiedImage
import xyz.robinjoon.agentdocs.workspace.application.port.output.ImageVerifier
import xyz.robinjoon.agentdocs.workspace.application.port.output.SceneVerifier
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetContent
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetMediaType
import xyz.robinjoon.agentdocs.workspace.domain.asset.InvalidAssetException

// Unknown formats are downloads, never trusted as executable MIME types. A drawing scene keeps its exact bytes.
internal class AttachmentVerifier(
    private val images: ImageVerifier,
    private val scenes: SceneVerifier,
) {
    // A public image address may only bring in an image.
    fun image(bytes: ByteArray): VerifiedImage =
        images.verify(bytes) ?: throw InvalidAssetException("only PNG, JPEG, GIF, WebP, and SVG images can be uploaded")

    fun verify(file: UploadedFile): VerifiedImage {
        if (file.bytes.isEmpty() || file.bytes.size > AssetContent.MAX_SIZE) {
            throw InvalidAssetException("a file must be 1 byte to 20MB")
        }
        val declared =
            file.declaredType
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase()
                .orEmpty()
        val image = images.verify(file.bytes)
        if (image != null) {
            requireType(declared, image.mediaType.mime)
            return image
        }
        if (declared.startsWith("image/")) throw InvalidAssetException("file content does not match its image type")
        return VerifiedImage(typeOf(file, declared), file.bytes)
    }

    private fun typeOf(
        file: UploadedFile,
        declared: String,
    ): AssetMediaType {
        val name = file.fileName.lowercase()
        if (isPdf(file.bytes)) {
            requireType(declared, AssetMediaType.PDF.mime)
            return AssetMediaType.PDF
        }
        if (declared == "application/pdf") throw InvalidAssetException("PDF content must start with %PDF-")
        if (declared == AssetMediaType.EXCALIDRAW.mime || name.endsWith(".excalidraw")) return scene(file, declared)
        if (declared == "text/html" || name.endsWith(".html") || name.endsWith(".htm")) {
            val text = textOf(file.bytes)
            if (!htmlStart.containsMatchIn(text)) throw InvalidAssetException("HTML must contain markup")
            requireType(declared, AssetMediaType.HTML.mime)
            return AssetMediaType.HTML
        }
        if (declared.startsWith("text/")) {
            textOf(file.bytes)
            return AssetMediaType.TEXT
        }
        return AssetMediaType.BINARY
    }

    private fun scene(
        file: UploadedFile,
        declared: String,
    ): AssetMediaType {
        if (declared !in sceneDeclarations) throw InvalidAssetException("file content does not match declared type $declared")
        scenes.verify(file.bytes)
        return AssetMediaType.EXCALIDRAW
    }

    private fun isPdf(bytes: ByteArray): Boolean =
        bytes.size >= PDF_HEADER.length && PDF_HEADER.indices.all { bytes[it] == PDF_HEADER[it].code.toByte() }

    private fun textOf(bytes: ByteArray): String {
        val text = bytes.toString(Charsets.UTF_8)
        if ('\u0000' in text || !text.toByteArray(Charsets.UTF_8).contentEquals(bytes)) {
            throw InvalidAssetException("text attachments must be UTF-8 without null bytes")
        }
        return text
    }

    private fun requireType(
        declared: String,
        actual: String,
    ) {
        if (declared.isNotEmpty() && declared != "application/octet-stream" && declared != actual) {
            throw InvalidAssetException("file content does not match declared type $declared")
        }
    }

    private companion object {
        const val PDF_HEADER = "%PDF-"

        // curl and browsers send an unknown extension as octet-stream, and Excalidraw's own files may come as JSON.
        val sceneDeclarations = setOf("", "application/octet-stream", "application/json", AssetMediaType.EXCALIDRAW.mime)
        val htmlStart = Regex("<[!a-zA-Z][^>]*>")
    }
}
