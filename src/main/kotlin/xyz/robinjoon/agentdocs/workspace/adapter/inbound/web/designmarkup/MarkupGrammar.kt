package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.designmarkup

import org.jsoup.nodes.Element
import org.jsoup.nodes.Range
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import java.math.BigDecimal

internal typealias Fields = Map<String, Any?>

internal fun invalid(message: String): Nothing = throw InvalidRequestException(message)

internal fun identifier(value: String): String {
    if (!Regex("[A-Za-z_][A-Za-z0-9_-]*").matches(value)) invalid("Invalid markup identifier: $value")
    return value
}

internal fun decimal(value: String): BigDecimal {
    val number = value.toBigDecimalOrNull() ?: invalid("Expected a number: $value")
    if (!number.toDouble().isFinite()) invalid("Expected a finite number: $value")
    return number
}

internal fun cssNumber(value: String): BigDecimal {
    if (!cssNumeric.matches(value)) invalid("Expected a decimal CSS number: $value")
    return decimal(value)
}

private val cssNumeric = Regex("""-?(?:\d+(?:\.\d+)?|\.\d+)""")

internal fun nonnegative(value: BigDecimal): BigDecimal {
    if (value.signum() < 0) invalid("Expected a nonnegative value")
    return value
}

internal fun Element.only(vararg allowed: String) {
    val bad = attributes().firstOrNull { it.key !in allowed }
    if (bad != null) invalid("Unsupported attribute '${bad.key}'")
}

internal fun Element.optional(attr: String): String? = if (hasAttr(attr)) attr(attr) else null

internal fun Element.textOnly(): String = childNodes().joinToString("") { (it as? TextNode)?.wholeText ?: invalid("Expected plain text") }

internal fun Element.layers(): List<Element> =
    childNodes().mapNotNull {
        when {
            it is Element -> it
            it is TextNode && it.isBlank -> null
            else -> invalid("Wrap text in span or p; unexpected node in <${tagName()}>")
        }
    }

internal fun <T> Element.located(action: () -> T): T =
    try {
        action()
    } catch (e: InvalidRequestException) {
        if (e.cause != null) throw e
        val pos = sourceRange().start()
        throw InvalidRequestException("<${tagName()}> at line ${pos.lineNumber()}, column ${pos.columnNumber()}: ${e.message}", cause = e)
    }

internal class MarkupIds(
    reserved: Iterable<String>,
    existing: Set<String> = emptySet(),
) {
    private val reservedNames = reserved.toSet() + existing
    private val used = existing.toMutableSet()
    private var number = 1

    fun allocate(
        given: String?,
        prefix: String,
    ): String {
        val id = given?.let(::identifier) ?: next(prefix)
        if (!used.add(id)) invalid("Duplicate persistent identifier: $id")
        return id
    }

    private fun next(prefix: String): String {
        var id: String
        do {
            id = prefix + number++
        } while (id in reservedNames)
        return id
    }
}

internal object MarkupGrammar {
    fun read(source: String): List<Element> {
        val parser = Parser.xmlParser().setTrackPosition(true).setTrackErrors(1)
        val document = parser.parseInput(source, "")
        parser.errors.firstOrNull()?.let { invalid("Invalid markup at ${it.cursorPos}: ${it.errorMessage}") }
        val spans = mutableListOf<Range>()
        document.traverse { node, depth ->
            if (depth > 160) invalid("Markup nesting exceeds 160 elements")
            if (node === document) return@traverse
            spans.add(node.sourceRange())
            if (node is Element) node.located { checkClosing(node, source) }
            if (node is Element) spans.add(node.endSourceRange())
        }
        checkCoverage(source, spans)
        return document.layers()
    }

    private fun checkCoverage(
        source: String,
        spans: List<Range>,
    ) {
        var end = 0
        for (span in spans.filter { it.isTracked }.sortedBy { it.startPos() }) {
            if (span.startPos() > end && source.substring(end, span.startPos()).isNotBlank()) gap(source, end)
            end = maxOf(end, span.endPos())
        }
        if (source.substring(end).isNotBlank()) gap(source, end)
    }

    private fun gap(
        source: String,
        offset: Int,
    ): Nothing {
        val before = source.take(offset)
        invalid("Unparsed markup at line " + (before.count { it == '\n' } + 1) + ", column " + (offset - before.lastIndexOf('\n')))
    }

    private fun checkClosing(
        e: Element,
        source: String,
    ) {
        val open = e.sourceRange()
        val close = e.endSourceRange()
        val selfClosed = source.substring(open.startPos(), open.endPos()).trimEnd().endsWith("/>")
        if (selfClosed && e.tagName() == "path") return
        if (!close.isTracked || close.isImplicit || close.startPos() < open.endPos()) invalid("Missing explicit closing tag")
        if (source.substring(close.startPos(), close.endPos()) != "</${e.tagName()}>") invalid("Mismatched closing tag")
    }
}
