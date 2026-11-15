package xyz.robinjoon.adocs

internal data class Fragment(
    val annotation: String?,
    val markdown: String,
) {
    val id: String? get() = annotation?.let { requireNotNull(Markdown.annotation.matchEntire(it)).groupValues[1] }
    val hash: String? get() = annotation?.let { requireNotNull(Markdown.annotation.matchEntire(it)).groupValues[2] }

    fun rendered(): String = listOfNotNull(annotation, markdown).joinToString("\n")
}

// Only frame fragments here. The server's MarkdownReader owns content interpretation and validation.
internal object Markdown {
    val annotation = Regex("<!--\\s*b:([a-z0-9]{4,16})\\s+h:(\\S+?)(?:\\s+cols:(\\S*?))?\\s*-->")
    private val fence = Regex("(`{3,}|~{3,}).*")
    private val single = Regex("\\s{0,3}(#{1,6}\\s+.*|[-*+]\\s+.*|\\d+[.)]\\s+.*|!\\[.*|---+|\\*\\*\\*+|___+)")

    fun header(text: String): String {
        val lines = text.replace("\r\n", "\n").lines()
        if (lines.firstOrNull() != "---") return ""
        val end = lines.drop(1).indexOf("---")
        require(end >= 0) { "unclosed front matter" }
        return lines.take(end + 2).joinToString("\n").trimEnd()
    }

    fun fragments(text: String): List<Fragment> {
        val body = text.replace("\r\n", "\n").removePrefix(header(text))
        val lines = body.lines().dropWhile(String::isBlank).dropLastWhile(String::isBlank)
        val result = arrayListOf<Fragment>()
        var index = 0
        var note: String? = null
        while (index < lines.size) {
            val line = lines[index].trim()
            when {
                line.isEmpty() -> {
                    index++
                }

                line.startsWith("<!--") && line.contains("b:") && !annotation.matches(line) -> {
                    throw CliFailure("invalid_annotation", "Restore original block comments")
                }

                annotation.matches(line) -> {
                    require(note == null) { "orphan annotation" }
                    note = line
                    index++
                }

                else -> {
                    val end = endOf(lines, index)
                    result += Fragment(note, lines.subList(index, end).joinToString("\n"))
                    note = null
                    index = end
                }
            }
        }
        return validateFragments(result, note)
    }

    private fun validateFragments(
        fragments: List<Fragment>,
        note: String?,
    ): List<Fragment> {
        require(note == null) { "orphan annotation" }
        val ids = fragments.mapNotNull(Fragment::id)
        require(ids.distinct().size == ids.size) { "duplicate block annotations" }
        return fragments
    }

    private fun endOf(
        lines: List<String>,
        start: Int,
    ): Int {
        val first = lines[start].trim()
        if (fence.matches(first) || first == "$$") return fenceEnd(lines, start)
        if (single.matches(first)) return start + 1
        if (first.startsWith('|')) return continuation(lines, start) { it.trim().startsWith('|') }
        if (first.startsWith('>')) return continuation(lines, start) { it.trim().startsWith('>') }
        return continuation(lines, start) {
            it.isNotBlank() && !annotation.matches(it.trim()) && !single.matches(it.trim()) &&
                !fence.matches(it.trim()) &&
                it.trim() != "$$"
        }
    }

    private fun continuation(
        lines: List<String>,
        start: Int,
        accepts: (String) -> Boolean,
    ): Int {
        var end = start + 1
        while (end < lines.size && accepts(lines[end])) end++
        return end
    }

    private fun fenceEnd(
        lines: List<String>,
        start: Int,
    ): Int {
        val opening = lines[start].trim().takeWhile { it == '`' || it == '~' || it == '$' }
        var end = start + 1
        while (end < lines.size && !closes(lines[end].trim(), opening)) end++
        require(end < lines.size) { "unclosed fenced fragment" }
        return end + 1
    }

    private fun closes(
        line: String,
        opening: String,
    ): Boolean = line.length >= opening.length && line.all { it == opening.first() }
}
