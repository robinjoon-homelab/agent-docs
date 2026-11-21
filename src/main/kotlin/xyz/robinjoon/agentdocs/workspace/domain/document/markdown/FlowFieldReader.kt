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

    fun int(key: String): Int = text(key).toIntOrNull() ?: throw InvalidDocumentException("invalid flow integer $key")

    private fun nullable(key: String): String? = text(key).ifEmpty { null }

    private fun all(key: String): List<String> = fields.filter { it.first == key }.map { it.second }

    private fun bool(key: String): Boolean =
        text(key).toBooleanStrictOrNull() ?: throw InvalidDocumentException("invalid flow boolean $key")

    fun frame(): FlowFrame = FlowFrame(nullable("frame"), optionalInt("frameW"), optionalInt("frameH"))

    private fun optionalInt(key: String): Int? =
        nullable(key)?.let {
            it.toIntOrNull() ?: throw InvalidDocumentException("invalid flow integer $key")
        }

    fun appearance(kind: String): FlowAppearance =
        FlowAppearance.of(
            kind,
            nullable("look"),
            if (kind == "list") all("item") else null,
            if (bool("hasDate")) text("date") else null,
        )

    fun placement(): FlowPlacement = FlowPlacement(optionalCoordinate("x"), optionalCoordinate("y"), int("w"), int("h"))

    private fun optionalCoordinate(key: String): Int = if (fields.none { it.first == key }) 0 else int(key)

    fun behavior(annotated: Boolean): FlowBehavior {
        val conditions = all("when")
        val targets = all("to")
        val modes = all("mode")
        if (conditions.size != targets.size || targets.size != modes.size) throw InvalidDocumentException("incomplete flow action")
        val actions = conditions.mapIndexed { index, condition -> FlowAction(condition, targets[index].ifEmpty { null }, modes[index]) }
        return FlowBehavior.of(FlowControls(bool("pinned"), nullable("group"), annotated), actions, all("state"))
    }
}
