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
    fun targets(blocks: List<Block>): Set<DocumentId> = blocks.flatMap(::inlineFields).flatMap(::targetsIn).toSet()

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

    private fun targetsIn(text: String): List<DocumentId> {
        val result = mutableListOf<DocumentId>()
        var at = 0
        while (at < text.length) {
            val (next, target) = scanAt(text, at)
            target?.let(result::add)
            at = next
        }
        return result
    }

    private fun scanAt(
        text: String,
        at: Int,
    ): Pair<Int, DocumentId?> {
        val skipped = skipped(text, at)
        if (skipped > 0) return at + skipped to null
        val end = labelEnd(text, at)
        if (end < 0) return at + 1 to null
        val close = destinationEnd(text, end + 1)
        if (close < 0) return at + 1 to null
        val destination = target.matchEntire(text.substring(end + 1, close)) ?: return close to null
        val id =
            if (text.getOrNull(at - 1) != '!' && balancedDestination(destination.value)) {
                DocumentId(UUID.fromString(destination.groupValues[1]))
            } else {
                null
            }
        return close to id
    }

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

    private fun balancedDestination(value: String): Boolean = value.startsWith("(<") == value.endsWith(">)")

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

    private val target = Regex("""\((?:doc:|<doc:)([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})>?\)""")
}
