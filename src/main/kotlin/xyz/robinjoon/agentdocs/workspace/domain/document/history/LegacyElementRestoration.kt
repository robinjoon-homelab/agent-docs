package xyz.robinjoon.agentdocs.workspace.domain.document.history

import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowDesignBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowElement
import xyz.robinjoon.agentdocs.workspace.domain.document.InvalidDocumentException

/** A v1 element lives in two places after upgrading: its node in the screen and its independent annotation. */
internal object LegacyElementRestoration {
    fun restore(
        current: List<Block>,
        projected: List<FlowDesignBlock>,
        element: FlowElement,
    ): List<Block> {
        val annotation = projected.single { it.id == element.id }
        val oldScreen = projected.single { it.kind == "screen" && it.designId == annotation.string("screen") }
        val screen =
            current.filterIsInstance<FlowDesignBlock>().singleOrNull { it.id == oldScreen.id && it.designId == oldScreen.designId }
                ?: throw InvalidDocumentException("restore the element's screen first")
        val root = screen.fields["root"] as? Map<*, *> ?: throw InvalidDocumentException("restore the element's frame first")
        val originalRoot = requireNotNull(oldScreen.fields["root"] as? Map<*, *>)
        val originals = (originalRoot["children"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
        val original = originals.single { it["id"] == element.id.value }
        val replaced = RestoredNode(original)
        val tree = replaced.visit(root)
        val restored = if (replaced.found) tree else insert(root, originalRoot, original, originals)
        return current.map { if (it.id == screen.id) screen.withFields(screen.fields + ("root" to restored)) else it }
    }

    private fun insert(
        root: Map<*, *>,
        originalRoot: Map<*, *>,
        original: Map<*, *>,
        originals: List<Map<*, *>>,
    ): Map<*, *> {
        if (root["id"] != originalRoot["id"] || root["kind"] != "box") throw InvalidDocumentException("restore the element's frame first")
        val children = (root["children"] as? List<*>).orEmpty()
        val positions = children.mapIndexed { index, child -> (child as? Map<*, *>)?.get("id") to index }.toMap()
        val old = originals.indexOf(original)
        val prior = originals.take(old).asReversed().firstNotNullOfOrNull { positions[it["id"]] }
        val index = prior?.plus(1) ?: originals.drop(old + 1).firstNotNullOfOrNull { positions[it["id"]] } ?: children.size
        return root + ("children" to (children.take(index) + listOf(original) + children.drop(index)))
    }

    private class RestoredNode(
        private val original: Map<*, *>,
    ) {
        var found = false
            private set

        fun visit(node: Map<*, *>): Map<*, *> {
            if (node["id"] == original["id"]) {
                found = true
                return original
            }
            return node.mapValues { (key, value) -> nested(key, value) }
        }

        private fun nested(
            key: Any?,
            value: Any?,
        ): Any? =
            when (key) {
                "children", "fallback", "template", "operands" -> nodes(value)
                "source" -> (value as? Map<*, *>)?.let(::visit)
                "slots" -> (value as? Map<*, *>)?.mapValues { nodes(it.value) }
                else -> value
            }

        private fun nodes(value: Any?): List<*>? = (value as? List<*>)?.map { if (it is Map<*, *>) visit(it) else it }
    }
}
