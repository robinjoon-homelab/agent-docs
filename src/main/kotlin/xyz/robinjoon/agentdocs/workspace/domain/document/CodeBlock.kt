package xyz.robinjoon.agentdocs.workspace.domain.document

// A fenced code block. The language "mermaid" makes it a diagram that editors render next to its source.
data class CodeBlock(
    override val id: BlockId,
    val language: String,
    val code: String,
) : Block {
    init {
        if (!languagePattern.matches(language)) {
            throw InvalidDocumentException("code language must be up to 30 letters, digits, or + # . _ -")
        }
        if (code.length > MAX_CODE_LENGTH) {
            throw InvalidDocumentException("code must be at most $MAX_CODE_LENGTH characters")
        }
        if (code.contains('\r')) {
            throw InvalidDocumentException("code uses \\n line endings")
        }
    }

    val isDiagram: Boolean get() = language == DIAGRAM_LANGUAGE

    override fun withId(id: BlockId): Block = copy(id = id)

    override fun plainText(): String = code

    companion object {
        const val MAX_CODE_LENGTH = 100_000
        const val DIAGRAM_LANGUAGE = "mermaid"
        private val languagePattern = Regex("[A-Za-z0-9+#._-]{0,30}")
    }
}
