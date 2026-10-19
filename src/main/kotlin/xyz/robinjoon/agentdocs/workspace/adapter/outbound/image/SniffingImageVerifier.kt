package xyz.robinjoon.agentdocs.workspace.adapter.outbound.image

import xyz.robinjoon.agentdocs.workspace.application.model.VerifiedImage
import xyz.robinjoon.agentdocs.workspace.application.port.output.ImageVerifier
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetMediaType

// Trusts the bytes, never the declared type or file name. SVG goes through SvgSanitizer; the raster formats are
// recognized by their file signatures.
class SniffingImageVerifier(
    private val sanitizer: SvgSanitizer,
) : ImageVerifier {
    override fun verify(bytes: ByteArray): VerifiedImage? {
        val raster = rasterTypeOf(bytes)
        if (raster != null) return VerifiedImage(raster, bytes)
        return sanitizer.sanitize(bytes)?.let { VerifiedImage(AssetMediaType.SVG, it) }
    }

    private fun rasterTypeOf(bytes: ByteArray): AssetMediaType? =
        when {
            bytes.startsWith(png) -> AssetMediaType.PNG

            bytes.startsWith(jpeg) -> AssetMediaType.JPEG

            bytes.startsWith(gif87) || bytes.startsWith(gif89) -> AssetMediaType.GIF

            bytes.startsWith(
                riff,
            ) && bytes.size >= WEBP_HEADER && bytes.copyOfRange(WEBP_OFFSET, WEBP_HEADER).contentEquals(webp) -> AssetMediaType.WEBP

            else -> null
        }

    private fun ByteArray.startsWith(signature: ByteArray): Boolean =
        size >= signature.size && copyOfRange(0, signature.size).contentEquals(signature)

    private companion object {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        val gif87 = "GIF87a".toByteArray()
        val gif89 = "GIF89a".toByteArray()
        val riff = "RIFF".toByteArray()
        val webp = "WEBP".toByteArray()
        const val WEBP_OFFSET = 8
        const val WEBP_HEADER = 12
    }
}
