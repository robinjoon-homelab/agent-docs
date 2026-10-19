package xyz.robinjoon.agentdocs.workspace.domain.document

// Where a document sits in its space, such as payments/retry-design. Folders are only the leading segments, so a
// folder exists while some document path starts with it and never has to be created.
@JvmInline
value class DocumentPath(
    val value: String,
) {
    init {
        val segments = value.split('/')
        if (value.length > MAX_LENGTH || segments.size > MAX_DEPTH || !segments.all(segmentPattern::matches)) {
            throw InvalidDocumentException(
                "a path is up to $MAX_DEPTH segments of letters, digits, '.', '_', or '-' separated by '/', at most $MAX_LENGTH characters",
            )
        }
    }

    val folder: String get() = value.substringBeforeLast('/', "")

    val name: String get() = value.substringAfterLast('/')

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 300
        const val MAX_DEPTH = 10
        private val segmentPattern = Regex("[\\p{L}\\p{N}._-]{1,80}")
    }
}
