package xyz.robinjoon.agentdocs.workspace.domain.document.markdown

import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId

// "<!-- b:id h:hash cols:c1,c2 -->" before a block. An id that is not a valid block id is ignored rather than
// rejected, so a mistyped annotation costs only the id, never the content.
internal data class Annotation(
    val id: BlockId?,
    val hash: String?,
    val columns: List<String>,
) {
    companion object {
        private val pattern = Regex("<!--\\s*b:(\\S+?)(?:\\s+h:(\\S+?))?(?:\\s+cols:(\\S*?))?\\s*-->")
        private val idPattern = Regex("[a-z0-9]{4,16}")

        fun parse(line: String): Annotation? {
            val match = pattern.matchEntire(line) ?: return null
            val (id, hash, columns) = match.destructured
            return Annotation(
                id = id.takeIf(idPattern::matches)?.let(::BlockId),
                hash = hash.ifEmpty { null },
                columns = columns.split(',').filter(String::isNotEmpty),
            )
        }
    }
}
