package xyz.robinjoon.agentdocs.workspace.domain.document

import java.math.BigDecimal
import java.util.Collections
import java.util.TreeMap

// Markdown and stored revisions enter through the domain reader without an HTTP or database JSON codec.
internal class DesignJsonReader(
    private val source: String,
) {
    private var offset = 0
    private val readValue =
        DeepRecursiveFunction<Unit, Any?> {
            skipSpace()
            when (val first = source.getOrNull(offset)) {
                '{' -> objectValue()
                '[' -> arrayValue()
                '"' -> stringValue()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                in '0'..'9', '-' -> number()
                else -> invalid("unexpected value $first")
            }
        }

    fun read(): Map<String, Any?> {
        skipSpace()
        if (source.getOrNull(offset) != '{') invalid("design must be a JSON object")
        val fields = objectValueWith { readValue(Unit) }
        skipSpace()
        if (offset != source.length) invalid("trailing content")
        return fields
    }

    private suspend fun DeepRecursiveScope<Unit, Any?>.objectValue(): Map<String, Any?> = objectValueWith { callRecursive(Unit) }

    private inline fun objectValueWith(next: () -> Any?): Map<String, Any?> {
        offset++
        val result = TreeMap<String, Any?>()
        if (take('}')) return emptyMap()
        do {
            val key = key(result)
            result[key] = next()
        } while (take(','))
        expect('}')
        return Collections.unmodifiableMap(result)
    }

    private fun key(existing: Map<String, Any?>): String {
        skipSpace()
        if (source.getOrNull(offset) != '"') invalid("expected object key")
        val key = stringValue()
        if (key in existing) invalid("duplicate object key")
        expect(':')
        return key
    }

    private suspend fun DeepRecursiveScope<Unit, Any?>.arrayValue(): List<Any?> {
        offset++
        val result = ArrayList<Any?>()
        if (take(']')) return emptyList()
        do {
            result.add(callRecursive(Unit))
        } while (take(','))
        expect(']')
        return Collections.unmodifiableList(result)
    }

    private fun stringValue(): String {
        offset++
        val result = StringBuilder()
        while (offset < source.length) {
            val c = source[offset++]
            when {
                c == '"' -> return result.toString()
                c == '\\' -> result.append(escape())
                c < ' ' -> invalid("unescaped control character")
                else -> result.append(c)
            }
        }
        invalid("unterminated string")
    }

    private fun escape(): Char {
        val next = source.getOrNull(offset++) ?: invalid("incomplete escape")
        if (next != 'u') return escapes[next] ?: invalid("invalid string escape")
        val hex = source.substring(offset, minOf(source.length, offset + 4))
        if (!hexPattern.matches(hex)) invalid("invalid Unicode escape")
        offset += 4
        return hex.toInt(16).toChar()
    }

    private fun number(): BigDecimal {
        val token = numberPattern.matchAt(source, offset)?.value ?: invalid("invalid number")
        offset += token.length
        val decimal = token.toBigDecimalOrNull() ?: invalid("invalid number")
        return try {
            decimal.stripTrailingZeros()
        } catch (failure: ArithmeticException) {
            throw InvalidDocumentException("flow-design JSON number exponent is out of range at offset $offset", failure)
        }
    }

    private fun literal(
        text: String,
        result: Any?,
    ): Any? {
        if (!source.startsWith(text, offset)) invalid("invalid literal")
        offset += text.length
        return result
    }

    private fun skipSpace() {
        while (offset < source.length && source[offset] in " \t\n\r") offset++
    }

    private fun take(c: Char): Boolean {
        skipSpace()
        if (source.getOrNull(offset) != c) return false
        offset++
        return true
    }

    private fun expect(c: Char) {
        if (!take(c)) invalid("expected $c")
    }

    private fun invalid(message: String): Nothing = throw InvalidDocumentException("flow-design JSON at offset $offset: $message")

    companion object {
        private val numberPattern = Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")
        private val hexPattern = Regex("[0-9a-fA-F]{4}")
        private val escapes =
            mapOf(
                '"' to '"',
                '\\' to '\\',
                '/' to '/',
                'b' to '\b',
                'f' to '\u000c',
                'n' to '\n',
                'r' to '\r',
                't' to '\t',
            )
    }
}
