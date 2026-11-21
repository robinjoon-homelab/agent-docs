package xyz.robinjoon.agentdocs.workspace.domain.document.markdown

import xyz.robinjoon.agentdocs.workspace.domain.document.FlowAction
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowAppearance
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowBehavior
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowControls
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowFrame
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowPlacement
import xyz.robinjoon.agentdocs.workspace.domain.document.InvalidDocumentException

internal class FlowFieldReader(
    private val fields: List<Pair<String, String>>,
) {
    fun text(key: String): String =
        fields.firstOrNull { it.first == key }?.second ?: throw InvalidDocumentException("missing flow field $key")

    private fun has(key: String): Boolean = fields.any { it.first == key }

    // Omitted optional attributes read as their empty default; replace restores the stored value afterwards.
    fun optionalText(key: String): String = fields.firstOrNull { it.first == key }?.second.orEmpty()

    fun int(key: String): Int = text(key).toIntOrNull() ?: throw InvalidDocumentException("invalid flow integer $key")

    private fun nullable(key: String): String? = optionalText(key).ifEmpty { null }

    private fun all(key: String): List<String> = fields.filter { it.first == key }.map { it.second }

    private fun bool(
        key: String,
        default: Boolean,
    ): Boolean =
        if (!has(key)) {
            default
        } else {
            text(key).toBooleanStrictOrNull() ?: throw InvalidDocumentException("invalid flow boolean $key")
        }

    fun frame(): FlowFrame = FlowFrame(nullable("frame"), optionalInt("frameW"), optionalInt("frameH"))

    private fun optionalInt(key: String): Int? =
        nullable(key)?.let {
            it.toIntOrNull() ?: throw InvalidDocumentException("invalid flow integer $key")
        }

    fun appearance(kind: String): FlowAppearance =
        FlowAppearance.of(
            kind,
            if (has("look")) nullable("look") else FlowAppearance.defaultLook(kind),
            if (kind == "list") all("item") else null,
            if (bool("hasDate", false)) optionalText("date") else null,
        )

    fun placement(): FlowPlacement = FlowPlacement(optionalCoordinate("x"), optionalCoordinate("y"), int("w"), int("h"))

    private fun optionalCoordinate(key: String): Int = if (fields.none { it.first == key }) 0 else int(key)

    fun behavior(annotated: Boolean): FlowBehavior {
        val conditions = all("when")
        val targets = all("to")
        val modes = all("mode")
        if (conditions.size != targets.size || targets.size != modes.size) throw InvalidDocumentException("incomplete flow action")
        val actions = conditions.mapIndexed { index, condition -> FlowAction(condition, targets[index].ifEmpty { null }, modes[index]) }
        return FlowBehavior.of(FlowControls(bool("pinned", false), nullable("group"), annotated), actions, all("state"))
    }
}
