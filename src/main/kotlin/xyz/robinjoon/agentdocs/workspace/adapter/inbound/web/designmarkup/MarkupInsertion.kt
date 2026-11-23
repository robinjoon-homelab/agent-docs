package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.designmarkup

import org.jsoup.nodes.Element
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowDesignBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.design.DesignAddress
import xyz.robinjoon.agentdocs.workspace.domain.document.design.stringFields

/** Append only to a box explicitly owned by this screen or component. Instance and repeat occurrences remain scoped. */
internal class MarkupInsertion(
    private val owner: FlowDesignBlock,
    private val parent: DesignAddress,
) {
    private val root = owner.fields["root"].stringFields()

    fun append(
        element: Element,
        resources: MarkupResources,
    ): FlowDesignBlock {
        if (owner.kind !in setOf("screen", "component") ||
            parent.via.isNotEmpty()
        ) {
            invalid("Choose a box directly owned by a screen or component")
        }
        val props = objects(owner.fields["props"]).filter { it["type"] == "text" }.mapTo(mutableSetOf()) { it.key() }
        val subtree = MarkupLayers(resources, props, element, occupied()).build(element)
        return owner.withFields(owner.fields + ("root" to insert(subtree)))
    }

    private fun insert(subtree: Fields): Fields {
        var matches = 0
        val update =
            DeepRecursiveFunction<Fields, Fields> { node ->
                val children = objects(node["children"])
                if (node["id"] == parent.node) {
                    if (node["kind"] != "box") invalid("The insertion parent must be a box")
                    matches++
                    return@DeepRecursiveFunction node + ("children" to (children + subtree))
                }
                if (children.isEmpty()) return@DeepRecursiveFunction node
                node + ("children" to children.map { callRecursive(it) })
            }
        val updated = update(root)
        if (matches != 1) invalid("Insertion parent is missing or ambiguous within the owner's children")
        return updated
    }

    private fun occupied(): Set<String> {
        val ids = mutableSetOf<String>()
        val walk =
            DeepRecursiveFunction<Any?, Unit> { value ->
                when (value) {
                    is List<*> -> {
                        value.forEach { callRecursive(it) }
                    }

                    is Map<*, *> -> {
                        if (value["kind"] in nodeKinds) (value["id"] as? String)?.let(ids::add)
                        value.values.forEach { callRecursive(it) }
                    }

                    else -> {
                        Unit
                    }
                }
            }
        walk(root)
        return ids
    }

    companion object {
        private val nodeKinds = setOf("box", "text", "instance", "group", "vector", "boolean", "mask", "image", "slot", "repeat", "shape")

        fun lastChild(
            owner: FlowDesignBlock,
            parent: DesignAddress,
        ): String? {
            val pending = ArrayDeque(listOf(owner.fields["root"].stringFields()))
            while (pending.isNotEmpty()) {
                val node = pending.removeLast()
                val children = objects(node["children"])
                if (node["id"] == parent.node) return children.lastOrNull()?.key()
                pending.addAll(children)
            }
            return null
        }
    }
}
