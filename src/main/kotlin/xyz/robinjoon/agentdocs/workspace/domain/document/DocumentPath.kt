package xyz.robinjoon.agentdocs.workspace.domain.document

// Where a document sits in its space, such as payments/retry-design. The leading segments are documents too: the
// document at payments is the parent of payments/retry-design. Nothing stores that relation, the path is all of it.
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

    val name: String get() = value.substringAfterLast('/')

    // Null at the top of the space.
    val parent: DocumentPath? get() = if ('/' in value) DocumentPath(value.substringBeforeLast('/')) else null

    // Every document above this one, the top first.
    val ancestors: List<DocumentPath> get() = generateSequence(parent) { it.parent }.toList().asReversed()

    // True for the path itself and every path under it, so payments/retry is within payments but not within pay.
    fun isWithin(other: DocumentPath): Boolean = value == other.value || value.startsWith("${other.value}/")

    // Where this path lands when the document at from, this one or an ancestor, moves to to. Fails when the result
    // breaks the path limits, so a move that would push a descendant too deep is refused before anything moves.
    fun movedFrom(
        from: DocumentPath,
        to: DocumentPath,
    ): DocumentPath {
        require(isWithin(from)) { "$value is not within $from" }
        return DocumentPath(to.value + value.removePrefix(from.value))
    }

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 300
        const val MAX_DEPTH = 10
        private val segmentPattern = Regex("[\\p{L}\\p{N}._-]{1,80}")
    }
}
