package xyz.robinjoon.agentdocs.workspace.domain.asset

// Immutable attachment formats. SVG is sanitized; HTML executes only in the isolated preview.
enum class AssetMediaType(
    val mime: String,
    val extension: String,
) {
    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg"),
    GIF("image/gif", "gif"),
    WEBP("image/webp", "webp"),
    SVG("image/svg+xml", "svg"),
    PDF("application/pdf", "pdf"),
    HTML("text/html", "html"),
    TEXT("text/plain", "txt"),
    BINARY("application/octet-stream", "bin"),
    ;

    val isImage: Boolean get() = mime.startsWith("image/")

    companion object {
        fun of(mime: String): AssetMediaType? = entries.firstOrNull { it.mime == mime.substringBefore(';').trim().lowercase() }
    }
}
