package xyz.robinjoon.agentdocs.workspace.domain.document

// The text of a block as GFM inline markdown: bold, italic, strikethrough, code spans, links, and <br> line breaks.
// Markdown is the shared language of people and agents, so the stored form is exactly what an agent reads and writes.
@JvmInline
value class InlineText(
    val markdown: String,
) {
    init {
        if (markdown.contains('\n') || markdown.contains('\r')) {
            throw InvalidDocumentException("inline text uses <br> instead of line breaks")
        }
        if (markdown != markdown.trim()) {
            throw InvalidDocumentException("inline text has no leading or trailing whitespace")
        }
        if (markdown.length > MAX_LENGTH) {
            throw InvalidDocumentException("inline text must be at most $MAX_LENGTH characters")
        }
    }

    // The text a reader sees. Comment anchors and search work on it, so formatting changes never move an anchor.
    fun plainText(): String = InlineMarkdown.plainText(markdown)

    override fun toString(): String = markdown

    companion object {
        const val MAX_LENGTH = 20_000
        val empty = InlineText("")
    }
}
