package xyz.robinjoon.agentdocs.workspace.domain.document

import java.nio.charset.StandardCharsets
import java.util.UUID

/** The old element's outer ID becomes its annotation's ID; the drawing lives inside the screen. */
object LegacyFlowUpgrade {
    fun convert(blocks: List<Block>): List<FlowDesignBlock> {
        FlowRules.validate("flow", blocks)
        if (blocks.first() is FlowDesignBlock) return blocks.filterIsInstance<FlowDesignBlock>()
        val settings = blocks.first() as FlowSettings
        val screens = blocks.filterIsInstance<FlowScreen>().associate { it.screenId to it.id.value }
        val result = mutableListOf(design(settings.id, settings(settings)))
        val occupied = blocks.map { it.id }.toMutableSet()
        FlowAppearance.kindNames().forEach { kind ->
            val id = generateSequence(0) { it + 1 }.map { componentBlockId(kind, it) }.first { occupied.add(it) }
            result.add(design(id, LegacyWireComponents.definition(kind)))
        }
        val boundaries = blocks.indices.filter { blocks[it] is FlowScreen } + blocks.size
        for ((start, end) in boundaries.zipWithNext()) {
            val screen = blocks[start] as FlowScreen
            val elements = blocks.subList(start + 1, end).filterIsInstance<FlowElement>()
            result.add(design(screen.id, screen(screen, elements)))
            elements.mapTo(result) { design(it.id, annotation(screen, it, screens)) }
        }
        return result
    }

    private fun componentBlockId(
        kind: String,
        salt: Int,
    ): BlockId {
        val bytes = "agent-docs/legacy-component/$kind/$salt".toByteArray(StandardCharsets.UTF_8)
        return BlockId(
            UUID
                .nameUUIDFromBytes(bytes)
                .toString()
                .replace("-", "")
                .take(16),
        )
    }

    private fun design(
        id: BlockId,
        fields: Map<String, Any?>,
    ): FlowDesignBlock = FlowDesignBlock.parse(id, DesignJsonWriter.write(fields))

    private fun settings(old: FlowSettings): Map<String, Any?> =
        mapOf(
            "kind" to "settings",
            "id" to old.id.value,
            "modelVersion" to 4,
            "idPrefix" to old.idPrefix,
            "nextScreen" to old.nextScreen,
            "frame" to old.frame,
        )

    private fun screen(
        old: FlowScreen,
        elements: List<FlowElement>,
    ): Map<String, Any?> =
        buildMap {
            put("kind", "screen")
            put("id", old.id.value)
            put("code", old.screenId)
            put("name", old.name)
            put("entry", old.entry)
            put("description", old.desc)
            val frame =
                if (old.frame.preset ==
                    "custom"
                ) {
                    mapOf("width" to old.frame.width, "height" to old.frame.height)
                } else {
                    old.frame.preset
                }
            if (frame != null) put("frame", frame)
            if (!old.frame.isNote) {
                put(
                    "root",
                    mapOf(
                        "kind" to "box",
                        "id" to "wire/root",
                        "layout" to mapOf("mode" to "free"),
                        "children" to elements.map(::instance),
                    ),
                )
            }
        }

    private fun instance(old: FlowElement): Map<String, Any?> =
        buildMap {
            put("kind", "instance")
            put("id", old.id.value)
            put("name", old.text.label)
            put("component", LegacyWireComponents.key(old.appearance.kind))
            put("accessibleName", old.text.label)
            put("inputs", LegacyWireComponents.inputs(old))
            put("position", mapOf("mode" to "absolute", "x" to mapOf("start" to old.placement.x), "y" to mapOf("start" to old.placement.y)))
            put("size", mapOf("x" to fixed(old.placement.w), "y" to fixed(old.placement.h)))
            if (old.behavior.pinned) put("locked", true)
            old.behavior.group?.let { put("group", it) }
            FlowAppearance.imageAsset(old)?.let { asset ->
                val image =
                    mapOf(
                        "id" to "wire/image/${old.id.value}",
                        "kind" to "image",
                        "asset" to asset.value.toString(),
                        "alt" to old.text.label,
                        "fit" to "contain",
                        "size" to LegacyWireComponents.fill,
                        "style" to mapOf("radius" to LegacyWireStyle.radius(8)),
                    )
                put("slots", mapOf("picture" to listOf(image)))
            }
        }

    private fun fixed(size: Int): Map<String, Any> = mapOf("mode" to "fixed", "px" to size)

    private fun annotation(
        screen: FlowScreen,
        old: FlowElement,
        screens: Map<String, String>,
    ): Map<String, Any?> =
        mapOf(
            "kind" to "annotation",
            "id" to old.id.value,
            "screen" to screen.id.value,
            "target" to mapOf("owner" to screen.id.value, "node" to old.id.value),
            "name" to old.text.label,
            "description" to old.text.desc,
            "enabled" to old.behavior.annotated,
            "legacyStateNames" to old.behavior.states,
            "actions" to
                old.behavior.actions.map { action ->
                    val target = action.to?.let { mapOf("screen" to (screens[it] ?: mapOf("unresolvedName" to it))) }
                    mapOf("when" to action.whenText, "mode" to action.mode) + if (target == null) emptyMap() else mapOf("to" to target)
                },
        )
}
