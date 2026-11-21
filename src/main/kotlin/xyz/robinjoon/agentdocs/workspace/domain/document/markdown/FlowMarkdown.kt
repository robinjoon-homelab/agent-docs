package xyz.robinjoon.agentdocs.workspace.domain.document.markdown

import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowDescription
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowElement
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowFields
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowScreen
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowSettings
import xyz.robinjoon.agentdocs.workspace.domain.document.InvalidDocumentException

// Attribute values use backslash escapes, including physical newlines, so user text cannot become block syntax.
object FlowMarkdown {
    fun recognizes(
        line: String,
        next: String?,
    ): Boolean =
        (line == "## flowSettings" && next?.startsWith("- frame:") == true) ||
            (screen.matches(line) && next?.startsWith("- entry:") == true) ||
            (element.matches(line) && next?.startsWith("- value:") == true)

    fun defaults(
        lines: List<String>,
        block: Block,
    ): xyz.robinjoon.agentdocs.workspace.domain.document.FlowDefaults =
        xyz.robinjoon.agentdocs.workspace.domain.document.FlowDefaults(
            block is FlowScreen && lines.first().startsWith("##  "),
            block is FlowElement && lines.none { it.startsWith("- x: ") },
            block is FlowElement && lines.none { it.startsWith("- y: ") },
        )

    fun write(block: Block): String {
        val head =
            when (block) {
                is FlowSettings -> {
                    "## flowSettings"
                }

                is FlowScreen -> {
                    "## ${block.screenId} ${escape(block.name)}"
                }

                is FlowElement -> {
                    "### ${FlowTextSyntax.label(block.text.label)} · ${block.appearance.kind}" +
                        if (block.behavior.annotated) "" else " · 모양만"
                }

                else -> {
                    throw InvalidDocumentException("not a flow block")
                }
            }
        val attributes = FlowFields.write(block).filter { it.first !in headingFields }
        val description =
            FlowFields
                .write(block)
                .firstOrNull { it.first == "desc" }
                ?.second
                .orEmpty()
        val body = if (description.isEmpty()) "" else "\n\n" + FlowTextSyntax.description(description)
        return head + attributes.joinToString("") { (key, value) -> "\n- $key:" + if (value.isEmpty()) "" else " ${escape(value)}" } + body
    }

    fun read(
        id: BlockId,
        lines: List<String>,
    ): Block {
        val head = lines.first()
        val attributes = lines.drop(1).takeWhile { it.startsWith("- ") }
        val description = lines.drop(1 + attributes.size).filter { it.isNotEmpty() }.joinToString("\n")
        val fields = attributes.map(::attribute) + ("desc" to unescape(description))
        val values = FlowFieldReader(fields)
        if (head == "## flowSettings") return FlowSettings(id, values.text("frame"), values.text("idPrefix"), values.int("nextScreen"))
        val screenMatch = screen.matchEntire(head)
        if (screenMatch != null) {
            return FlowScreen(
                id,
                screenMatch.groupValues[1].ifEmpty { "S-00" },
                unescape(screenMatch.groupValues[2]),
                values.text("entry"),
                values.frame(),
                values.text("desc"),
            )
        }
        val match = element.matchEntire(head) ?: throw InvalidDocumentException("invalid flow heading")
        return FlowElement(
            id,
            values.appearance(match.groupValues[2]),
            values.placement(),
            FlowDescription(unescape(match.groupValues[1]), values.text("value"), values.text("desc")),
            values.behavior(match.groupValues[3].isEmpty()),
        )
    }

    private fun attribute(line: String): Pair<String, String> {
        if (!line.startsWith("- ") || !line.contains(":")) throw InvalidDocumentException("invalid flow attribute")
        return line.substring(2).substringBefore(":") to unescape(line.substringAfter(":").removePrefix(" "))
    }

    fun escape(value: String): String = FlowTextSyntax.value(value)

    fun unescape(value: String): String = FlowTextSyntax.read(value)

    private val headingFields = setOf("screenId", "name", "kind", "label", "annotated", "desc")
    private val screen = Regex("## ([A-Z0-9]{0,4}-[0-9]{2,}|) (.*)")
    private val element = Regex("### (.*) · ([a-z]+)( · 모양만)?")
}
