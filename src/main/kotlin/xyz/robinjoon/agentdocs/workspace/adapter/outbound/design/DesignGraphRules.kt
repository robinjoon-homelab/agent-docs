package xyz.robinjoon.agentdocs.workspace.adapter.outbound.design

import tools.jackson.databind.JsonNode
import xyz.robinjoon.agentdocs.workspace.application.model.DesignValidationWarning

internal object DesignGraphRules {
    fun validate(root: JsonNode): List<DesignValidationWarning> {
        val blocks = root.path("blocks").values()
        uniqueDesignFields(blocks, "id", "design document")
        val index = blocks.associateBy { it.path("id").stringValue() }
        val byKind = blocks.groupBy { it.path("kind").stringValue() }
        val settings = byKind["settings"].orEmpty().singleOrNull()
        if (settings?.path("modelVersion")?.intValue() != 4) designGraphError("exactly one v4 settings block is required")
        val screens = byKind["screen"].orEmpty()
        if (screens.isEmpty()) designGraphError("a design needs at least one screen")
        uniqueDesignFields(screens, "code", "screen codes")
        val libraries = byKind["tokens"].orEmpty()
        if (libraries.size > 1) designGraphError("a design can have at most one token library")
        val warnings = linkedSetOf<DesignValidationWarning>()
        var owner = libraries.firstOrNull()?.designString("id").orEmpty()
        val warn: (String, String) -> Unit = { code, detail -> warnings.add(DesignValidationWarning(owner, code, detail)) }
        val tokens = DesignTokenRules(libraries.firstOrNull()) { token -> warn("missingToken", token) }
        tokens.validateLibrary()
        val dependencies = HashMap<String, Set<String>>()
        for (block in blocks) {
            owner = block.designString("id")
            val used = validateBlock(block, index, tokens, warn)
            if (block.path("kind").stringValue() == "component") dependencies[block.path("id").stringValue()] = used
        }
        DesignGraphNodes.checkAcyclic(dependencies, "component")
        return warnings.toList()
    }

    private fun validateBlock(
        block: JsonNode,
        blocks: Map<String, JsonNode>,
        tokens: DesignTokenRules,
        warn: (String, String) -> Unit,
    ): Set<String> {
        val kind = block.path("kind").stringValue()
        val owner = block.path("id").stringValue()
        when (kind) {
            "state", "annotation" -> validateOwner(block, blocks)
            "component" -> validateProperties(block.path("props"), owner)
        }
        val nodes = DesignGraphNodes.ownedBy(block).toList()
        uniqueDesignFields(nodes, "id", "block '$owner'")
        nodes.forEach { validateNode(it, tokens) }
        block.path("variants").forEach { tokens.validateFields(it.path("set")) }
        block.path("changes").forEach { validatePatchValues(it, tokens) }
        if (kind == "annotation") annotation(block, blocks, warn)
        return nodes.filter { it.path("kind").stringValue() == "instance" }.mapNotNull { component(it, blocks, warn) }.toSet()
    }

    private fun annotation(
        block: JsonNode,
        blocks: Map<String, JsonNode>,
        warn: (String, String) -> Unit,
    ) {
        if (block.path("enabled").asBoolean(true) &&
            block.designString("description").isBlank()
        ) {
            warn("missingDescription", block.designString("id"))
        }
        block.path("actions").forEach { validateAction(it, blocks, warn) }
    }

    private fun validateNode(
        node: JsonNode,
        tokens: DesignTokenRules,
    ) {
        DesignGeometryRules.validate(node)
        tokens.validateFields(node)
        node.path("overrides").forEach { validatePatchValues(it, tokens) }
    }

    private fun validatePatchValues(
        operation: JsonNode,
        tokens: DesignTokenRules,
    ) {
        listOf("fields", "set").mapNotNull(operation::get).forEach(tokens::validateFields)
        if (operation.path("op").stringValue() in setOf("leaf", "detailInsert", "detailSet")) {
            listOf("value", "item").mapNotNull(operation::get).forEach(tokens::validateVisual)
        }
    }

    private fun validateProperties(
        props: JsonNode,
        owner: String,
    ) {
        uniqueDesignFields(props, "id", "properties of '$owner'")
        for (prop in props) {
            val id = prop.path("id").stringValue()
            val choices = prop.path("choices")
            uniqueDesignFields(choices, "id", "choices of '$owner/$id'")
            val initial = prop.get("initial")
            if (choices.isArray && initial != null && choices.none { it.path("id") == initial }) {
                designGraphError("undeclared default choice for '$owner/$id'")
            }
            prop.get("fields")?.let { validateProperties(it, "$owner/$id") }
        }
    }

    private fun validateOwner(
        block: JsonNode,
        blocks: Map<String, JsonNode>,
    ) {
        val screenId = block.path("screen").stringValue()
        val screen = blocks[screenId]
        if (screen?.path("kind")?.stringValue() != "screen") designGraphError("missing owning screen '$screenId'")
        if (block.path("kind").stringValue() == "state" && screen.path("frame").stringValue(null) == "note") {
            designGraphError("a note cannot own design states")
        }
        val state = block.get("state") ?: return
        val scope = blocks[state.stringValue()]
        if (scope?.path("kind")?.stringValue() != "state" || scope.path("screen") != block.path("screen")) {
            designGraphError("annotation state belongs to a different screen or is missing")
        }
    }

    private fun component(
        instance: JsonNode,
        blocks: Map<String, JsonNode>,
        warn: (String, String) -> Unit,
    ): String? {
        val reference = instance.path("component")
        val id = reference.stringValue(null)
        val target = blocks[id]
        if (target == null) {
            warn("missingComponent", "${instance.designString("id")}: ${reference.asReference()}")
            return null
        }
        if (target.path("kind").stringValue() != "component") designGraphError("instance refers to '$id', which is not a component")
        return id
    }

    private fun validateAction(
        action: JsonNode,
        blocks: Map<String, JsonNode>,
        warn: (String, String) -> Unit,
    ) {
        val to = action.path("to")
        val screen = byReference(to.path("screen"), "screen", blocks)
        val state = byReference(to.path("state"), "state", blocks)
        if (to.has("screen") && screen == null) warn("missingScreen", to.path("screen").asReference())
        if (to.has("state") && state == null) warn("missingState", to.path("state").asReference())
        if (screen != null && state != null && state.path("screen") != screen.path("id")) {
            designGraphError("navigation state belongs to another screen")
        }
    }

    private fun JsonNode.asReference(): String = stringValue(null) ?: designString("unresolvedName")

    private fun byReference(
        reference: JsonNode,
        kind: String,
        blocks: Map<String, JsonNode>,
    ): JsonNode? {
        val id = reference.stringValue(null) ?: return null
        val target = blocks[id] ?: return null
        if (target.path("kind").stringValue() != kind) designGraphError("navigation $kind refers to another block kind")
        return target
    }
}
