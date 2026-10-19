package xyz.robinjoon.agentdocs.workspace.domain.document

// Computes the text a reader sees in inline markdown exactly as the web editor does (frontend
// src/domain/document/inline.ts), because text anchors are offsets into this string on both sides: a difference moves
// or breaks comments. Delimiter runs pair by the CommonMark stack with its rule of 3, and anything that does not pair
// stays literal, so an unusual sequence never loses characters. Whitespace means JavaScript's \s, not Java's.
internal object InlineMarkdown {
    fun plainText(markdown: String): String = parse(markdown, inLink = false)

    private fun parse(
        source: String,
        inLink: Boolean,
    ): String {
        val nodes = tokenize(source, inLink)
        pairDelimiters(nodes.filterIsInstance<Delimiter>())
        return nodes.joinToString("") { it.text }
    }

    private fun tokenize(
        source: String,
        inLink: Boolean,
    ): List<Node> {
        val nodes = mutableListOf<Node>()
        var index = 0
        while (index < source.length) {
            val scan = scanAt(source, index, inLink) ?: Scan(Literal(source.substring(index, index + 1)), index + 1)
            nodes += scan.node
            index = scan.next
        }
        return nodes
    }

    private fun scanAt(
        source: String,
        index: Int,
        inLink: Boolean,
    ): Scan? =
        when (source[index]) {
            '\\' -> backslash(source, index)
            '`' -> backticks(source, index)
            '*', '~' -> delimiter(source, index)
            '\n' -> Scan(Literal("\n"), index + 1)
            '<' -> lineBreakTag.matchAt(source, index)?.let { Scan(Literal("\n"), index + it.value.length) }
            '[' -> if (inLink) null else link(source, index)
            else -> null
        }

    private fun backslash(
        source: String,
        index: Int,
    ): Scan {
        val next = source.getOrNull(index + 1)
        return if (isAsciiPunctuation(next)) Scan(Literal(next.toString()), index + 2) else Scan(Literal("\\"), index + 1)
    }

    // A code span closes on a backtick run of the same length; its content shows with newlines as spaces and one
    // padding space trimmed from each side.
    private fun backticks(
        source: String,
        index: Int,
    ): Scan {
        val run = runLength(source, index, '`')
        val close = codeClose(source, index + run, run)
        if (close < 0) return Scan(Literal("`".repeat(run)), index + run)
        val content = source.substring(index + run, close).replace('\n', ' ')
        val padded = content.startsWith(' ') && content.endsWith(' ') && content.any { !isWhitespace(it) }
        return Scan(Literal(if (padded) content.substring(1, content.length - 1) else content), close + run)
    }

    private fun codeClose(
        source: String,
        from: Int,
        length: Int,
    ): Int {
        var at = source.indexOf('`', from)
        while (at >= 0 && runLength(source, at, '`') != length) {
            at = source.indexOf('`', at + runLength(source, at, '`'))
        }
        return at
    }

    // Only ~~ is strikethrough; a single ~ or a longer run is literal. _ is never markup.
    private fun delimiter(
        source: String,
        index: Int,
    ): Scan {
        val char = source[index]
        val length = runLength(source, index, char)
        if (char == '~' && length != 2) return Scan(Literal("~".repeat(length)), index + length)
        val canOpen = !isWhitespace(source.getOrNull(index + length))
        val canClose = !isWhitespace(source.getOrNull(index - 1))
        return Scan(Delimiter(char, length, canOpen, canClose), index + length)
    }

    // [text](target) shows its text only; links do not nest. An image's ! stays text, as in the editor.
    private fun link(
        source: String,
        index: Int,
    ): Scan? {
        val textEnd = linkTextEnd(source, index)
        if (textEnd < 0) return null
        val end = destinationEnd(source, textEnd + 1)
        if (end < 0) return null
        return Scan(Literal(parse(source.substring(index + 1, textEnd), inLink = true)), end)
    }

    // The ] that closes the [ at open, counting nested brackets and skipping escapes and code spans.
    private fun linkTextEnd(
        source: String,
        open: Int,
    ): Int {
        var depth = 0
        var index = open
        while (index < source.length) {
            val skipped = skippedInLinkText(source, index)
            depth += if (skipped > 0) 0 else depthChange(source[index], '[', ']')
            if (skipped == 0 && source[index] == ']' && depth == 0) return index
            index += maxOf(skipped, 1)
        }
        return -1
    }

    private fun skippedInLinkText(
        source: String,
        index: Int,
    ): Int =
        when {
            isEscape(source, index) -> 2
            source[index] == '`' -> codeSpanLength(source, index)
            else -> 0
        }

    private fun codeSpanLength(
        source: String,
        index: Int,
    ): Int {
        val run = runLength(source, index, '`')
        val close = codeClose(source, index + run, run)
        return if (close < 0) run else close + run - index
    }

    private fun depthChange(
        char: Char,
        open: Char,
        close: Char,
    ): Int =
        when (char) {
            open -> 1
            close -> -1
            else -> 0
        }

    // The index after a (target) that starts at open, or -1 when there is none.
    private fun destinationEnd(
        source: String,
        open: Int,
    ): Int =
        when {
            source.getOrNull(open) != '(' -> -1
            source.getOrNull(open + 1) == '<' -> angleDestinationEnd(source, open + 1)
            else -> bareDestinationEnd(source, open + 1)
        }

    private fun angleDestinationEnd(
        source: String,
        start: Int,
    ): Int {
        val close = source.indexOf('>', start + 1)
        if (close < 0 || source.getOrNull(close + 1) != ')') return -1
        val target = source.substring(start + 1, close).replace("\\<", "").replace("\\>", "")
        return if ('<' in target || '\n' in target) -1 else close + 2
    }

    // A bare target has no whitespace and balanced parentheses.
    private fun bareDestinationEnd(
        source: String,
        start: Int,
    ): Int {
        var depth = 0
        var index = start
        while (index < source.length && !isWhitespace(source[index])) {
            if (source[index] == ')' && depth == 0) return index + 1
            depth += depthChange(source[index], '(', ')')
            index += if (isEscape(source, index)) 2 else 1
        }
        return -1
    }

    // Each closer pairs with the nearest opener it can, two characters at a time while both have two; openers between
    // them can no longer pair, and what stays unpaired is text.
    private fun pairDelimiters(delimiters: List<Delimiter>) {
        val openers = mutableListOf<Delimiter>()
        delimiters.forEach { delimiter ->
            if (delimiter.canClose) close(delimiter, openers)
            if (delimiter.remaining > 0 && delimiter.canOpen) openers += delimiter
        }
    }

    private fun close(
        closer: Delimiter,
        openers: MutableList<Delimiter>,
    ) {
        var position = openers.indexOfLast { canPair(it, closer) }
        while (closer.remaining > 0 && position >= 0) {
            val opener = openers[position]
            val used = if (closer.char == '~' || (opener.remaining >= 2 && closer.remaining >= 2)) 2 else 1
            opener.remaining -= used
            closer.remaining -= used
            openers.subList(if (opener.remaining == 0) position else position + 1, openers.size).clear()
            position = openers.indexOfLast { canPair(it, closer) }
        }
    }

    // CommonMark's rule of 3: when either run could both open and close, the two may not pair if their lengths add up
    // to a multiple of 3, unless both lengths are multiples of 3.
    private fun canPair(
        opener: Delimiter,
        closer: Delimiter,
    ): Boolean {
        if (opener.char != closer.char || opener.remaining == 0) return false
        if (opener.char == '~') return opener.remaining == 2 && closer.remaining == 2
        val ambiguous = opener.canClose || closer.canOpen
        val multipleOfThree = (opener.length + closer.length) % RULE_OF_THREE == 0
        return !(ambiguous && multipleOfThree && (opener.length % RULE_OF_THREE != 0 || closer.length % RULE_OF_THREE != 0))
    }

    private fun runLength(
        source: String,
        start: Int,
        char: Char,
    ): Int {
        var end = start
        while (end < source.length && source[end] == char) end++
        return end - start
    }

    private fun isAsciiPunctuation(char: Char?): Boolean = char != null && char in asciiPunctuation

    private fun isEscape(
        source: String,
        index: Int,
    ): Boolean = source[index] == '\\' && isAsciiPunctuation(source.getOrNull(index + 1))

    // Text edges count as whitespace, so a run at the start of a block can open and one at the end can close.
    private fun isWhitespace(char: Char?): Boolean = char == null || char in javaScriptWhitespace

    private sealed interface Node {
        val text: String
    }

    private class Literal(
        override val text: String,
    ) : Node

    private class Delimiter(
        val char: Char,
        val length: Int,
        val canOpen: Boolean,
        val canClose: Boolean,
    ) : Node {
        var remaining = length

        override val text: String get() = char.toString().repeat(remaining)
    }

    private class Scan(
        val node: Node,
        val next: Int,
    )

    private const val RULE_OF_THREE = 3
    private const val JAVA_SCRIPT_WHITESPACE =
        "\t\n\u000B\u000C\r              " +
            "    　"
    private val javaScriptWhitespace = JAVA_SCRIPT_WHITESPACE.toSet()
    private val asciiPunctuation = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~".toSet()
    private val lineBreakTag = Regex("<br[$JAVA_SCRIPT_WHITESPACE]*/?>", RegexOption.IGNORE_CASE)
}
