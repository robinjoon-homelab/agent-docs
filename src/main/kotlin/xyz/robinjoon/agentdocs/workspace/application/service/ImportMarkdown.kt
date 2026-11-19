package xyz.robinjoon.agentdocs.workspace.application.service

import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException

internal object ImportMarkdown {
    fun rewrite(
        markdown: String,
        upload: (String) -> String,
    ): String {
        var fence: String? = null
        return markdown.splitToSequence('\n').joinToString("\n") { line ->
            val marker = fenceMarker.find(line)?.groupValues?.get(1)
            if (marker != null) {
                fence = nextFence(fence, marker)
                line
            } else if (fence != null) {
                line
            } else {
                rewriteLine(line, upload)
            }
        }
    }

    private fun nextFence(
        current: String?,
        marker: String,
    ): String? =
        when {
            current == null -> marker
            marker.first() == current.first() && marker.length >= current.length -> null
            else -> current
        }

    private fun rewriteLine(
        line: String,
        upload: (String) -> String,
    ): String {
        var cursor = 0
        return buildString {
            inlineCode.findAll(line).forEach { code ->
                append(rewriteImages(line.substring(cursor, code.range.first), upload))
                append(code.value)
                cursor = code.range.last + 1
            }
            append(rewriteImages(line.substring(cursor), upload))
        }
    }

    private fun rewriteImages(
        text: String,
        upload: (String) -> String,
    ): String {
        if (imageStart.containsMatchIn(image.replace(text, ""))) throw InvalidRequestException("unsupported_image_syntax")
        return image.replace(text) { match ->
            val destination = match.groupValues[2].removeSurrounding("<", ">")
            if (destination.startsWith("asset:")) throw InvalidRequestException("unsupported_image_syntax")
            "![${match.groupValues[1]}](asset:${upload(destination)}${match.groupValues[3]})"
        }
    }

    private val fenceMarker = Regex("^ {0,3}(`{3,}|~{3,})")
    private val image = Regex("""!\[((?:\\.|[^\]\\])*)]\((<[^>]+>|[^\s()]+)(\s+"(?:\\.|[^"\\])*")?\)""")
    private val imageStart = Regex("""(?<!\\)!\[""")
    private val inlineCode = Regex("""(`+).*?\1""")
}
