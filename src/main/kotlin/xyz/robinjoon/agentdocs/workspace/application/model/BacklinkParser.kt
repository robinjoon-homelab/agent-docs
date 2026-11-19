package xyz.robinjoon.agentdocs.workspace.application.model

import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.Callout
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.Heading
import xyz.robinjoon.agentdocs.workspace.domain.document.ListItem
import xyz.robinjoon.agentdocs.workspace.domain.document.Paragraph
import xyz.robinjoon.agentdocs.workspace.domain.document.Quote
import xyz.robinjoon.agentdocs.workspace.domain.document.TableBlock
import java.util.UUID

// Parse inline fields, never rendered plain text or fenced code bodies.
object BacklinkParser {
    private data class Origin(
        val scheme: String,
        val host: String,
        val port: Int,
    )

    fun targets(
        blocks: List<Block>,
        appBaseUrl: String? = null,
    ): Set<DocumentId> {
        val origin = appBaseUrl?.let(::originOf)
        return blocks.flatMap(::inlineFields).flatMap { targetsIn(it, origin) }.toSet()
    }

    private fun inlineFields(block: Block): List<String> =
        when (block) {
            is Paragraph -> listOf(block.text.markdown)
            is Heading -> listOf(block.text.markdown)
            is Quote -> listOf(block.text.markdown)
            is Callout -> listOf(block.text.markdown)
            is ListItem -> listOf(block.text.markdown)
            is TableBlock -> block.rows.flatMap { row -> row.cells.map { it.markdown } }
            else -> emptyList()
        }

    private fun targetsIn(
        text: String,
        appOrigin: Origin?,
    ): List<DocumentId> {
        val result = mutableListOf<DocumentId>()
        var at = 0
        while (at < text.length) {
            val (next, target) = scanAt(text, at, appOrigin)
            target?.let(result::add)
            at = next
        }
        return result
    }

    private fun scanAt(
        text: String,
        at: Int,
        appOrigin: Origin?,
    ): Pair<Int, DocumentId?> {
        val skipped = skipped(text, at)
        if (skipped > 0) return at + skipped to null
        val end = labelEnd(text, at)
        if (end < 0) return at + 1 to null
        val close = destinationEnd(text, end + 1)
        if (close < 0) return at + 1 to null
        if (text.getOrNull(at - 1) == '!') return close to null
        return close to parseDestination(text.substring(end + 1, close), appOrigin)
    }

    private fun parseDestination(
        wrapped: String,
        appOrigin: Origin?,
    ): DocumentId? {
        val raw = unwrapDestination(wrapped) ?: return null
        docTarget.matchEntire(raw)?.let { return DocumentId(UUID.fromString(it.groupValues[1])) }
        if (raw.startsWith("/")) return parseRelativePath(raw)
        if (appOrigin == null) return null
        return parseAppUrl(raw, appOrigin)
    }

    private fun unwrapDestination(wrapped: String): String? {
        if (!wrapped.startsWith("(") || !wrapped.endsWith(")")) return null
        val inner = wrapped.substring(1, wrapped.length - 1)
        if (inner.startsWith("<") != inner.endsWith(">")) return null
        return if (inner.startsWith("<")) inner.substring(1, inner.length - 1) else inner
    }

    private fun parseRelativePath(raw: String): DocumentId? {
        if (raw.startsWith("//") || raw.startsWith("/\\")) return null
        val path = raw.substringBefore('?').substringBefore('#')
        return canonicalPath.matchEntire(path)?.let { DocumentId(UUID.fromString(it.groupValues[1])) }
    }

    private fun parseAppUrl(
        raw: String,
        appOrigin: Origin,
    ): DocumentId? {
        val (origin, path) = splitHttpUrl(raw) ?: return null
        if (origin != appOrigin) return null
        return canonicalPath.matchEntire(path)?.let { DocumentId(UUID.fromString(it.groupValues[1])) }
    }

    private fun originOf(baseUrl: String): Origin? = splitHttpUrl(baseUrl.trim())?.first

    private fun splitHttpUrl(raw: String): Pair<Origin, String>? {
        val match = httpUrl.matchEntire(raw) ?: return null
        val scheme = match.groupValues[1].lowercase()
        val host = match.groupValues[2].lowercase()
        val portText = match.groupValues[3]
        val port = if (portText.isNotEmpty()) portText.toIntOrNull() ?: return null else defaultPort(scheme)
        val path = match.groupValues[4].ifEmpty { "/" }
        return Origin(scheme, host, port) to path
    }

    private fun defaultPort(scheme: String): Int = if (scheme == "https") HTTPS_PORT else HTTP_PORT

    private fun labelEnd(
        text: String,
        start: Int,
    ): Int {
        if (text[start] != '[') return -1
        var depth = 1
        var at = start + 1
        while (at < text.length) {
            val skip = skipped(text, at)
            if (skip > 0) {
                at += skip
                continue
            }
            if (text[at] == '[') depth++
            if (text[at] == ']') depth--
            if (depth == 0) return at
            at++
        }
        return -1
    }

    private fun destinationEnd(
        text: String,
        start: Int,
    ): Int {
        if (text.getOrNull(start) != '(') return -1
        var depth = 1
        var at = start + 1
        while (at < text.length && !text[at].isWhitespace()) {
            depth += parenthesisDepth(text[at])
            if (depth == 0) return at + 1
            at += if (text[at] == '\\') 2 else 1
        }
        return -1
    }

    private fun parenthesisDepth(character: Char): Int =
        when (character) {
            '(' -> 1
            ')' -> -1
            else -> 0
        }

    private fun skipped(
        text: String,
        at: Int,
    ): Int {
        if (text[at] == '\\') return minOf(2, text.length - at)
        if (text[at] != '`') return 0
        val run = text.substring(at).takeWhile { it == '`' }.length
        var end = at + run
        while (end < text.length) {
            val found = text.indexOf('`', end)
            if (found < 0) return run
            val length = text.substring(found).takeWhile { it == '`' }.length
            if (length == run) return found + run - at
            end = found + length
        }
        return run
    }

    private const val HTTP_PORT = 80
    private const val HTTPS_PORT = 443
    private const val UUID_PATTERN = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    private val docTarget = Regex("""doc:($UUID_PATTERN)""")
    private val canonicalPath = Regex("""/(?:d|s/[^/?#]+/d)/($UUID_PATTERN)""")
    private val httpUrl =
        Regex("""^(https?)://([^/@?#:\[\]]+|\[[^\]]+\])(?::(\d+))?(/[^?#]*)?(?:\?[^#]*)?(?:#.*)?$""", RegexOption.IGNORE_CASE)
}
