package xyz.robinjoon.agentdocs.workspace.domain.document.markdown

import java.util.Collections

// The "key: value" lines between the leading "---" fences. Values are plain words, double-quoted strings, or
// bracketed lists, which covers every field the platform writes.
class FrontMatter(
    entries: List<Pair<String, String>>,
) {
    val entries: List<Pair<String, String>> = Collections.unmodifiableList(entries.toList())

    fun text(key: String): String? = entries.firstOrNull { it.first == key }?.second?.let(::unquote)

    fun list(key: String): List<String>? =
        entries
            .firstOrNull { it.first == key }
            ?.second
            ?.trim()
            ?.removePrefix("[")
            ?.removeSuffix("]")
            ?.split(',')
            ?.map { unquote(it.trim()) }
            ?.filter { it.isNotEmpty() }

    fun render(): String = if (entries.isEmpty()) "" else entries.joinToString("\n", "---\n", "\n---") { (key, value) -> "$key: $value" }

    companion object {
        val empty = FrontMatter(emptyList())

        fun quote(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

        fun list(values: List<String>): String = values.joinToString(", ", "[", "]")

        private fun unquote(value: String): String {
            val trimmed = value.trim()
            if (trimmed.length < 2 || !trimmed.startsWith("\"") || !trimmed.endsWith("\"")) return trimmed
            return trimmed.substring(1, trimmed.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
        }
    }
}
