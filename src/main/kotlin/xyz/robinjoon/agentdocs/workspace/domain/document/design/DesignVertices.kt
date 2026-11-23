package xyz.robinjoon.agentdocs.workspace.domain.document.design

import java.math.BigDecimal

data class DesignVertexPath(
    val contour: String?,
    val vertex: String,
) {
    fun parts(): List<Map<String, String>> =
        listOfNotNull(contour?.let { mapOf("collection" to "contours", "id" to it) }) + mapOf("collection" to "vertices", "id" to vertex)

    companion object {
        fun from(raw: Any?): DesignVertexPath {
            val steps = raw as? List<*> ?: invalidDesign("parts must be an ID path")
            if (steps.size == 1) return DesignVertexPath(null, member(steps.single(), "vertices"))
            if (steps.size != 2) invalidDesign("expected [vertices] or [contours, vertices]")
            return DesignVertexPath(member(steps.first(), "contours"), member(steps.last(), "vertices"))
        }

        private fun member(
            value: Any?,
            collection: String,
        ): String {
            val step = value as? DesignFields ?: invalidDesign("part needs a collection and an id")
            if (step.keys != setOf("collection", "id") || step["collection"] != collection) invalidDesign("expected $collection ID")
            return (step["id"] as? String)?.takeIf(String::isNotEmpty) ?: invalidDesign("part ID must be a nonempty string")
        }
    }
}

data class DesignVertex(
    val path: DesignVertexPath,
    val x: BigDecimal,
    val y: BigDecimal,
)

// Contains just the geometry needed by a paged coordinate read. Other paint, text, and input data stays in its owner.
class DesignVertices(
    occurrence: DesignOccurrence,
    scene: DesignScene,
) {
    private val groups = linkedMapOf<String?, MutableList<DesignVertex>>()
    private val contourGeometry = occurrence.node["contours"] is List<*>

    init {
        val node = occurrence.node
        if (node["kind"] != "vector") invalidDesign("vertex edits require a vector node")
        val network = node["network"] as? DesignFields
        if (network != null) groups[null] = vertices(null, network["vertices"])
        node["contours"].objects().forEach(::putContour)
        scene.detailChanges(occurrence.target).forEach(::apply)
    }

    fun rows(): Sequence<DesignVertex> = groups.values.asSequence().flatten()

    fun resolve(path: DesignVertexPath): DesignVertex =
        groups[path.contour]?.singleOrNull { it.path == path } ?: invalidDesign("vertex is missing or ambiguous; read parts again")

    private fun putContour(contour: DesignFields) {
        val id = contour["id"] as? String ?: invalidDesign("contour needs an ID")
        groups[id] = vertices(id, contour["vertices"])
    }

    private fun vertices(
        contour: String?,
        items: Any?,
    ): MutableList<DesignVertex> = items.objects().map { point(contour, it) }.toMutableList()

    private fun point(
        contour: String?,
        item: DesignFields,
    ): DesignVertex {
        val id = item["id"] as? String ?: invalidDesign("vertex needs an ID")
        val x = item["x"] as? BigDecimal ?: invalidDesign("vertex needs x")
        val y = item["y"] as? BigDecimal ?: invalidDesign("vertex needs y")
        return DesignVertex(DesignVertexPath(contour, id), x, y)
    }

    private fun apply(change: DesignFields) {
        if (change["op"] == "detailInsert") {
            insert(change)
            return
        }
        val steps = change["parts"].objects()
        if (steps.firstOrNull()?.get("collection") !in geometryCollections) return
        if (steps.size == 1 && steps.first()["collection"] == "contours") {
            contourChange(change, steps.first()["id"] as? String)
            return
        }
        if (steps.last()["collection"] != "vertices") return
        val path = DesignVertexPath.from(steps)
        val group = groups[path.contour] ?: return
        val index = group.indexOfFirst { it.path == path }
        if (index >= 0) editPoint(change, group, index)
    }

    private fun editPoint(
        change: DesignFields,
        group: MutableList<DesignVertex>,
        index: Int,
    ) {
        val old = group[index]
        when (change["op"]) {
            "detailRemove" -> {
                group.removeAt(index)
            }

            "detailMove" -> {
                move(group, index, change["at"]) { it.path.vertex }
            }

            "detailSet" -> {
                val value = change["value"] as? BigDecimal ?: return
                if (change["field"] == "x") group[index] = old.copy(x = value)
                if (change["field"] == "y") group[index] = old.copy(y = value)
            }
        }
    }

    private fun contourChange(
        change: DesignFields,
        contour: String?,
    ) {
        if (contour == null || contour !in groups) return
        when (change["op"]) {
            "detailRemove" -> {
                groups.remove(contour)
            }

            "detailMove" -> {
                val order = groups.keys.filterNotNull().toMutableList()
                move(order, order.indexOf(contour), change["at"]) { it }
                orderContours(order)
            }
        }
    }

    private fun insert(change: DesignFields) {
        val item = change["item"] as? DesignFields ?: return
        when (change["collection"]) {
            "contours" -> {
                insertContour(item, change["at"])
            }

            "vertices" -> {
                val parent = change["parents"].objects().singleOrNull()?.get("id") as? String
                val group = groups[parent] ?: return
                val at = position(group, change["at"]) { it.path.vertex } ?: return
                group.add(at, point(parent, item))
            }
        }
    }

    private fun insertContour(
        item: DesignFields,
        at: Any?,
    ) {
        if (!contourGeometry) return
        val order = groups.keys.filterNotNull().toMutableList()
        val location = position(order, at) { it } ?: return
        val id = item["id"] as? String ?: invalidDesign("contour needs an ID")
        order.add(location, id)
        putContour(item)
        orderContours(order)
    }

    private fun orderContours(order: List<String>) {
        val sorted = order.associateWith { groups.getValue(it) }
        groups.clear()
        groups.putAll(sorted)
    }

    private fun <T> move(
        items: MutableList<T>,
        index: Int,
        at: Any?,
        id: (T) -> String,
    ) {
        val destination = position(items, at, id) ?: return
        val value = items.removeAt(index)
        items.add(if (destination > index) destination - 1 else destination, value)
    }

    private fun <T> position(
        items: List<T>,
        at: Any?,
        id: (T) -> String,
    ): Int? {
        val anchor = at as? DesignFields ?: return null
        if (anchor["edge"] == "first") return 0
        if (anchor["edge"] == "last") return items.size
        val key = anchor["before"] ?: anchor["after"]
        val index = items.indexOfFirst { id(it) == key }
        if (index < 0) return null
        return if ("after" in anchor) index + 1 else index
    }

    private companion object {
        val geometryCollections = setOf("vertices", "contours")
    }
}
