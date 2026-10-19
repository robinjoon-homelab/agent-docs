package xyz.robinjoon.agentdocs.workspace.domain.asset

// The image types an editor can show inline. SVG is accepted only after its scripts are removed.
enum class AssetMediaType(
    val mime: String,
    val extension: String,
) {
    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg"),
    GIF("image/gif", "gif"),
    WEBP("image/webp", "webp"),
    SVG("image/svg+xml", "svg"),
    ;

    companion object {
        fun of(mime: String): AssetMediaType? = entries.firstOrNull { it.mime == mime.substringBefore(';').trim().lowercase() }
    }
}
