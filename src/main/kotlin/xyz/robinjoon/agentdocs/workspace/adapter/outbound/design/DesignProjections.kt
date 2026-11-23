package xyz.robinjoon.agentdocs.workspace.adapter.outbound.design

import tools.jackson.databind.JsonNode

internal class DesignProjections(
    private val blocks: Map<String, JsonNode>,
    private val inputs: DesignInputRules,
) {
    private val annotations =
        blocks.values.filter { it.designString("kind") == "annotation" }.groupBy {
            it.get("state")?.stringValue()
                ?: it.designString("screen")
        }

    fun check(block: JsonNode) {
        val kind = block.designString("kind")
        val screen = if (kind == "state") blocks[block.designString("screen")] ?: return else block
        val root = screen.get("root") ?: return
        val scope = DesignProjectionScope(screen.designString("id"), DesignBindingScope(props = screen.designProps()))
        val tree = DesignProjectionTree(blocks, inputs)
        tree.emit(root, scope, null)
        if (kind == "component") tree.variants(block.path("variants"), scope)
        if (kind == "state") changes(block, tree, scope)
        tree.finish(annotations[block.designString("id")].orEmpty())
    }

    private fun changes(
        state: JsonNode,
        tree: DesignProjectionTree,
        screen: DesignProjectionScope,
    ) {
        val additions = screen.copy(owner = state.designString("id"))
        val writes = state.path("changes").values().map { DesignProjectionEdit(it, additions, listOf(additions.owner, "changes")) }
        val (inputsFirst, nodeChanges) = writes.partition { initialInput(it.change, additions.owner) }
        inputsFirst.forEach(tree::stateEdit)
        nodeChanges.forEach(tree::stateEdit)
    }

    private fun initialInput(
        op: JsonNode,
        state: String,
    ): Boolean {
        val target = op.path("target")
        if (target.designString("owner") == state || target.path("via").any { it.designString("owner") == state }) return false
        return op.designString("op") in DesignProjectionEdits.inputOperations
    }
}
