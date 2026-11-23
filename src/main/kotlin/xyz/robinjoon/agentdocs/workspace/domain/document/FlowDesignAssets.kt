package xyz.robinjoon.agentdocs.workspace.domain.document

import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetMediaType

internal class FlowDesignAssets(
    private val blocks: List<FlowDesignBlock>,
) {
    private val types = DesignAssetTypes()
    private val inputs = DesignAssetInputs(types)

    fun requirements(): Map<AssetId, Set<AssetMediaType>> {
        // Collect definitions before uses: screens, components and states may appear in either order.
        for (block in blocks) visit(block.fields) { _, node -> inputs.index(block.designId, node) }
        for (block in blocks) {
            collectDescription(block)
            visit(block.fields) { field, node -> references(block.designId, field, node) }
        }
        return types.result()
    }

    private fun collectDescription(block: FlowDesignBlock) {
        if (block.kind !in descriptionKinds) return
        val text = block.string("description") ?: return
        InlineMarkdown.assetIds(text).forEach { types.consume(it.value.toString(), DesignAssetTypes.any, null) }
    }

    private fun references(
        owner: String,
        field: String,
        node: Map<*, *>,
    ) {
        val component = inputs.componentAt(node["target"])
        inputs.values(owner, node["component"] as? String, node["inputs"])
        inputs.values(owner, component, (node["fields"] as? Map<*, *>)?.get("inputs"))
        inputs.values(owner, component, (node["set"] as? Map<*, *>)?.get("inputs"))
        inputs.change(owner, component, node)
        if (node["op"] == "leaf" || node["op"] == "detailSet") {
            val path = (node["path"] ?: node["field"])?.toString().orEmpty()
            direct(owner, path.substringAfterLast('.'), node["value"])
        }
        if (field in fonts && "kind" !in node) consume(owner, node["asset"], DesignAssetTypes.font)
        if (field == "image" || field == "fields" || field == "set" || node["kind"] == "image") {
            consume(owner, node["asset"], DesignAssetTypes.image)
        }
        consume(owner, node["svgAsset"], DesignAssetTypes.svg)
        if (node["kind"] == "image") consume(owner, node["image"], DesignAssetTypes.image)
    }

    private fun direct(
        owner: String,
        field: String,
        value: Any?,
    ) {
        when (field) {
            "asset" -> consume(owner, value, DesignAssetTypes.image)
            "svgAsset" -> consume(owner, value, DesignAssetTypes.svg)
            "image" -> consume(owner, (value as? Map<*, *>)?.get("asset") ?: value, DesignAssetTypes.image)
            "font" -> consume(owner, (value as? Map<*, *>)?.get("asset"), DesignAssetTypes.font)
        }
    }

    private fun consume(
        owner: String,
        value: Any?,
        allowed: Set<AssetMediaType>,
    ) {
        types.consume(value, allowed, inputs.binding(owner, value))
    }

    private fun visit(
        root: Map<*, *>,
        inspect: (String, Map<*, *>) -> Unit,
    ) {
        val pending = ArrayDeque<Pair<String, Any?>>()
        pending.addLast("" to root)
        while (pending.isNotEmpty()) {
            val (field, value) = pending.removeLast()
            when (value) {
                is List<*> -> {
                    pending.addAll(array(field, value))
                }

                is Map<*, *> -> {
                    inspect(field, value)
                    pending.addAll(children(value))
                }
            }
        }
    }

    private fun array(
        field: String,
        list: List<*>,
    ): List<Pair<String, Any?>> = list.map { field to it }

    private fun children(node: Map<*, *>): List<Pair<String, Any?>> =
        node.entries.flatMap { (raw, value) ->
            val key = raw.toString()
            when {
                key == "inputs" || key == "initial" -> {
                    emptyList()
                }

                key == "fields" && "itemKey" in node -> {
                    emptyList()
                }

                key == "value" && node["op"] in inputEdits -> {
                    emptyList()
                }

                key == "slots" -> {
                    (value as? Map<*, *>)?.values.orEmpty().map { "children" to it }
                }

                key == "value" && node["op"] in fieldEdits -> {
                    listOf((node["path"] ?: node["field"]).toString().substringAfterLast('.') to value)
                }

                else -> {
                    listOf(key to value)
                }
            }
        }

    private companion object {
        val descriptionKinds = setOf("screen", "state", "annotation")
        val inputEdits = setOf("input", "itemSet")
        val fieldEdits = setOf("leaf", "detailSet")
        val fonts = setOf("font", "fallback")
    }
}
