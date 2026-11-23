package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import xyz.robinjoon.agentdocs.workspace.application.model.DocumentView
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.DesignJsonWriter
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowDesignBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.design.DesignAddress
import xyz.robinjoon.agentdocs.workspace.domain.document.design.DesignOccurrence
import xyz.robinjoon.agentdocs.workspace.domain.document.design.DesignScene
import xyz.robinjoon.agentdocs.workspace.domain.document.design.DesignVertices
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal class DesignBrowse(
    view: DocumentView,
    private val request: DesignRequest,
    private val documentUrl: String,
) {
    private val version = view.document.version
    private val blocks = view.document.blocks.filterIsInstance<FlowDesignBlock>()
    private val hashes = mutableMapOf<BlockId, String>()
    private val keyword = request.optionalString("query").orEmpty().trim()
    private val kind = request.optionalString("kind")

    fun page(): Map<String, Any?> {
        if (blocks.isEmpty()) throw InvalidRequestException("this document has no v4 design")
        val skip = offset()
        val limit = request.limit()
        val items = ArrayList<Map<String, Any?>>()
        val iterator = browseRows().drop(skip).iterator()
        var bytes = 0
        while (iterator.hasNext() && items.size < limit) {
            val item = iterator.next()
            val size = DesignJsonWriter.write(item).toByteArray().size
            if (size > PAGE_BYTES) throw InvalidRequestException("one address exceeds the page budget; narrow the scope or use docs_read")
            if (bytes + size > PAGE_BYTES) return response(items, skip + items.size)
            bytes += size
            items += item
        }
        return response(items, if (iterator.hasNext()) skip + items.size else null)
    }

    private fun browseRows(): Sequence<Map<String, Any?>> =
        when (request.optionalString("view") ?: if (request.selected == null) "blocks" else "nodes") {
            "blocks" -> blocks()
            "library" -> DesignLibrary(blocks, request, ::hash).rows()
            "nodes" -> nodes()
            "parts" -> parts()
            else -> throw InvalidRequestException("view must be blocks, library, nodes or parts")
        }

    private fun response(
        items: List<Map<String, Any?>>,
        next: Int?,
    ): Map<String, Any?> {
        val page = mapOf("version" to version, "items" to items, "nextCursor" to next?.let { "$version.$it" })
        if (items.isNotEmpty()) return page
        val hint =
            if (request.screen == null) {
                "Search screens and states together: omit kind, use a single word or board number as query."
            } else {
                "Omit kind or query to list this scope. For nested scroll containers use view=nodes and query=scrollable."
            }
        return page + ("hint" to hint)
    }

    private fun offset(): Int {
        val cursor = request.optionalString("cursor") ?: return 0
        val parts = cursor.split('.')
        if (parts.size != 2 || parts[0].toIntOrNull() != version) throw InvalidRequestException("cursor is stale or invalid; start again")
        return parts[1].toIntOrNull()?.takeIf { it >= 0 } ?: throw InvalidRequestException("invalid cursor")
    }

    private fun blocks(): Sequence<Map<String, Any?>> {
        val screen = request.screen?.let { DesignScene(blocks, it, request.state).screen }
        return blocks
            .asSequence()
            .filter { kind == null || it.kind == kind }
            .filter { screen == null || it.id == screen.id || it.string("screen") == screen.designId }
            .filter { keyword.isEmpty() || matches(listOf(it.designId, it.string("code"), it.plainText()).joinToString(" ")) }
            .map(::blockRow)
    }

    private fun parts(): Sequence<Map<String, Any?>> {
        val screen = request.screen ?: throw InvalidRequestException("view=parts requires screen and target")
        val scene = DesignScene(blocks, screen, request.state)
        val occurrence = scene.resolve(request.target())
        val common =
            mapOf(
                "screen" to scene.screen.id.value,
                "state" to scene.state?.id?.value,
                "target" to occurrence.target.fields(),
                "parent" to occurrence.parent?.fields(),
                "guards" to occurrence.dependencies.associate { it.id.value to hash(it) },
                "url" to link(screen, request.state, occurrence.target),
            )
        return DesignVertices(occurrence, scene)
            .rows()
            .filter { keyword.isEmpty() || matches(it.path.vertex) || matches(it.path.contour) }
            .map { common + mapOf("parts" to it.path.parts(), "x" to it.x, "y" to it.y) }
    }

    private fun blockRow(block: FlowDesignBlock): Map<String, Any?> {
        val parent = if (block.kind == "screen") block else blocks.find { it.kind == "screen" && it.designId == block.string("screen") }
        return mapOf(
            "block" to block.id.value,
            "kind" to block.kind,
            "designId" to block.designId,
            "hash" to hash(block),
            "name" to block.string("name")?.take(180),
            "code" to block.string("code"),
            "description" to block.string("description")?.take(180),
            "screen" to parent?.id?.value,
            "state" to block.id.value.takeIf { block.kind == "state" },
            "url" to parent?.let { link(it.id, block.id.takeIf { _ -> block.kind == "state" }) },
        )
    }

    private fun nodes(): Sequence<Map<String, Any?>> {
        val screen = request.screen ?: throw InvalidRequestException("view=nodes requires a screen block ID from view=blocks")
        val scene = DesignScene(blocks, screen, request.state)
        request.selected?.let { return sequenceOf(nodeRow(scene.resolve(it), scene)) }
        return scene
            .occurrences()
            .filter { kind == null || kind == "node" || it.node["kind"] == kind }
            .filter {
                keyword.isEmpty() ||
                    listOf(
                        it.target.node,
                        it.target.owner,
                        it.node["component"],
                        it.node["name"],
                        it.node["alias"],
                        it.node["accessibleName"],
                        it.valueOf(it.node["text"]),
                        scrollRole(it.node),
                    ).any(::matches)
            }.map { nodeRow(it, scene) }
    }

    private fun nodeRow(
        occurrence: DesignOccurrence,
        scene: DesignScene,
    ): Map<String, Any?> =
        mapOf(
            "screen" to scene.screen.id.value,
            "state" to scene.state?.id?.value,
            "target" to occurrence.target.fields(),
            "parent" to occurrence.parent?.fields(),
            "baseFields" to preview(occurrence.node),
            "stateFields" to stateFields(occurrence.target, scene),
            "kind" to occurrence.node["kind"],
            "name" to (occurrence.node["name"] as? String)?.take(180),
            "component" to (occurrence.node["component"] as? String),
            "overflow" to (occurrence.node["style"] as? Map<*, *>)?.get("overflow"),
            "guards" to occurrence.dependencies.associate { it.id.value to hash(it) },
            "url" to link(scene.screen.id, scene.state?.id, occurrence.target),
        )

    private fun stateFields(
        target: DesignAddress,
        scene: DesignScene,
    ): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        for (change in scene.state?.fields?.get("changes") as? List<*> ?: emptyList<Any?>()) {
            if (change !is Map<*, *> || !target.matches(change["target"])) continue
            when {
                change["op"] == "set" -> (change["fields"] as? Map<*, *>)?.let { result.putAll(preview(it)) }
                change["op"] == "hide" -> result["visible"] = false
                change["op"] == "leaf" && change["path"] in displayed -> result[change["path"].toString()] = short(change["value"])
            }
        }
        return result
    }

    private fun preview(fields: Map<*, *>): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        for (key in listOf("visible", "text")) if (key in fields) result[key] = short(fields[key])
        val scroll = fields["scroll"] as? Map<*, *> ?: return result
        for (axis in listOf("x", "y")) if (axis in scroll) result["scroll.$axis"] = short(scroll[axis])
        return result
    }

    private fun short(value: Any?): Any? =
        when (value) {
            is String -> value.take(180)
            is Number, is Boolean -> value
            else -> "(structured)"
        }

    private fun hash(block: FlowDesignBlock): String = hashes.getOrPut(block.id) { BlockMarkdown.hash(block) }

    private fun scrollRole(node: Map<*, *>): String? {
        val overflow = (node["style"] as? Map<*, *>)?.get("overflow")
        val axes = if (overflow is Map<*, *>) overflow.values else listOf(overflow)
        return if (axes.any { it == "scroll" || it == "auto" }) "scrollable 스크롤" else null
    }

    private fun matches(value: Any?): Boolean {
        if (value !is String) return false
        if (value.contains(keyword, ignoreCase = true)) return true
        return keyword.split(' ').filter(String::isNotBlank).all { word ->
            val term = word.removeSuffix("번").takeIf { it.isNotEmpty() && it.all(Char::isDigit) } ?: word
            value.contains(term, ignoreCase = true)
        }
    }

    fun link(
        screen: BlockId,
        state: BlockId?,
        target: DesignAddress? = null,
    ): String {
        val params = mutableMapOf("flowScreen" to screen.value)
        if (state != null) params["flowState"] = state.value
        if (target != null) {
            params["flowOwner"] = target.owner
            params["flowElement"] = target.node
            if (target.via.isNotEmpty()) params["flowVia"] = DesignJsonWriter.write(target.via)
        }
        return documentUrl + "?" + params.entries.joinToString("&") { it.key + "=" + URLEncoder.encode(it.value, StandardCharsets.UTF_8) }
    }

    companion object {
        private val displayed = setOf("text", "visible", "scroll.x", "scroll.y")
        private const val PAGE_BYTES = 24 * 1024
    }
}
