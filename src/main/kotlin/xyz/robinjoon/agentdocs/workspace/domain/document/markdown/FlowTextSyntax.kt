package xyz.robinjoon.agentdocs.workspace.domain.document.markdown

import xyz.robinjoon.agentdocs.workspace.domain.document.InvalidDocumentException

internal object FlowTextSyntax {
    fun value(text: String): String =
        text
            .map { char ->
                if (text.isBlank() && char.isWhitespace()) {
                    "\\u" + char.code.toString(16).padStart(4, '0')
                } else {
                    escapes[char] ?: char.toString()
                }
            }.joinToString("")

    fun label(text: String): String = value(text).replace(" · ", " \\m ")

    fun description(text: String): String {
        val encoded = value(text)
        return if (syntax.containsMatchIn(encoded)) "\\" + encoded else encoded
    }

    fun read(text: String): String {
        val result = StringBuilder()
        var index = 0
        while (index < text.length) {
            val current = text[index++]
            if (current != '\\') {
                result.append(current)
            } else {
                val code = text.getOrNull(index++) ?: throw InvalidDocumentException("unfinished flow escape")
                val decoded = escaped(text, index, code)
                result.append(decoded.first)
                index += decoded.second
            }
        }
        return result.toString()
    }

    private fun escaped(
        text: String,
        at: Int,
        code: Char,
    ): Pair<Char, Int> {
        if (code != 'u') return (unescapes[code] ?: code) to 0
        val value = text.substring(at, minOf(at + 4, text.length))
        if (value.length != 4) throw InvalidDocumentException("incomplete unicode escape")
        val number = value.toIntOrNull(16) ?: throw InvalidDocumentException("invalid unicode escape")
        return number.toChar() to 4
    }

    private val escapes = mapOf('\\' to "\\\\", '\n' to "\\n", '\r' to "\\r", '\t' to "\\t")
    private val unescapes = mapOf('n' to '\n', 'r' to '\r', 't' to '\t', 'm' to '·', 'h' to '#', 'l' to '<', 'd' to '-', 's' to ' ')
    private val syntax = Regex("^(?: {0,3}(?:[-*+]\\s|#{1,6}\\s|<|>|[0-9]+[.)]\\s|```|~~~|\\||!\\[)|---$)")
}
