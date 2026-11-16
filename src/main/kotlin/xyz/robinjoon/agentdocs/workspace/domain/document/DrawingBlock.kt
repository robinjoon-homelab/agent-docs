package xyz.robinjoon.agentdocs.workspace.domain.document

import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import java.util.UUID

// A hand-drawn diagram. The Excalidraw scene is the original and the cleaned SVG is what readers and agents see; both
// are immutable assets, so editing a drawing points the same block at a new pair and old revisions keep theirs.
data class DrawingBlock(
    override val id: BlockId,
    val svg: AssetId,
    val scene: AssetId,
    val alt: String,
) : Block {
    init {
        if (alt.length > ImageBlock.MAX_TEXT_LENGTH || alt.contains('\n') || alt.contains('\r')) {
            throw InvalidDocumentException("a drawing's alt text must be one line of at most ${ImageBlock.MAX_TEXT_LENGTH} characters")
        }
        if (svg == scene) throw InvalidDocumentException("a drawing needs two different assets: its SVG and its scene")
    }

    override fun withId(id: BlockId): Block = copy(id = id)

    // Search and whole-block comment quotes read the alt text, as they do for images; scene text is not indexed.
    override fun plainText(): String = alt

    companion object {
        // The image caption that marks a drawing; what follows is the scene's asset id.
        const val CAPTION_PREFIX = "excalidraw asset:"
        private val captionPattern = Regex(Regex.escape(CAPTION_PREFIX) + "([0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12})")

        // The scene an image of svg with this caption names, or null. Only the exact prefix followed by a canonical
        // UUID other than svg counts; every other caption, a near miss included, belongs to an ordinary image. Reading
        // markdown and building an image block both ask this, so neither can hold what the other reads as a drawing.
        fun sceneIn(
            svg: AssetId,
            caption: String,
        ): AssetId? =
            captionPattern
                .matchEntire(caption)
                ?.let { AssetId(UUID.fromString(it.groupValues[1])) }
                ?.takeIf { it != svg }
    }
}
