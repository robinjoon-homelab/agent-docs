package xyz.robinjoon.agentdocs.workspace.domain.document

import java.math.BigDecimal

internal class DesignJsonWriter private constructor() {
    private val output = StringBuilder()
    private val emit =
        DeepRecursiveFunction<Any?, Unit> { value ->
            when (value) {
                is Map<*, *> -> members(value)
                is List<*> -> elements(value)
                is String -> string(value)
                is BigDecimal -> output.append(number(value))
                else -> output.append(value)
            }
            Unit
        }

    private suspend fun DeepRecursiveScope<Any?, Unit>.members(values: Map<*, *>) {
        output.append('{')
        for ((index, entry) in values.entries.withIndex()) {
            if (index > 0) output.append(',')
            string(entry.key as? String ?: throw InvalidDocumentException("flow-design JSON object keys must be strings"))
            output.append(':')
            callRecursive(entry.value)
        }
        output.append('}')
    }

    private suspend fun DeepRecursiveScope<Any?, Unit>.elements(values: List<*>) {
        output.append('[')
        for ((index, element) in values.withIndex()) {
            if (index > 0) output.append(',')
            callRecursive(element)
        }
        output.append(']')
    }

    private fun number(value: BigDecimal): String {
        val normalized = value.stripTrailingZeros()
        val exponent = normalized.precision().toLong() - normalized.scale() - 1
        return if (exponent in -6..20) normalized.toPlainString() else normalized.toString().lowercase()
    }

    private fun string(value: String) {
        output.append('"')
        for (c in value) {
            val escaped = escapes[c]
            when {
                escaped != null -> output.append(escaped)
                c < ' ' || c.isSurrogate() -> output.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                else -> output.append(c)
            }
        }
        output.append('"')
    }

    companion object {
        private val escapes =
            mapOf(
                '"' to "\\\"",
                '\\' to "\\\\",
                '\b' to "\\b",
                '\u000c' to "\\f",
                '\n' to "\\n",
                '\r' to "\\r",
                '\t' to "\\t",
            )

        fun write(value: Any?): String {
            val writer = DesignJsonWriter()
            writer.emit(value)
            return writer.output.toString()
        }
    }
}
