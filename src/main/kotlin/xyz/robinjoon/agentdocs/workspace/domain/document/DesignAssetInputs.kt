package xyz.robinjoon.agentdocs.workspace.domain.document

/** Only property declarations give meaning to the arbitrary keys in inputs and list item fields. */
internal class DesignAssetInputs(
    private val types: DesignAssetTypes,
) {
    private val declarations = mutableMapOf<DesignAssetProperty, Map<*, *>>()
    private val repeats = mutableMapOf<Pair<String, String>, Map<*, *>>()
    private val instances = mutableMapOf<Pair<String, String>, String>()

    fun index(
        owner: String,
        obj: Map<*, *>,
    ) {
        val id = obj["id"] as? String ?: return
        when (obj["kind"]) {
            "component" -> properties(DesignAssetProperty(owner, emptyList()), obj["props"])
            "instance" -> (obj["component"] as? String)?.let { instances[owner to id] = it }
            "repeat" -> (obj["items"] as? Map<*, *>)?.let { repeats[owner to id] = it }
        }
    }

    private fun properties(
        parent: DesignAssetProperty,
        fields: Any?,
    ) {
        val props = (fields as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()
        for (prop in props) {
            val id = prop["id"] as? String ?: continue
            val key = parent.copy(path = parent.path + id)
            declarations[key] = prop
            if (prop["type"] == "asset") types.input(key, prop["initial"], null)
            if (prop["type"] == "list") properties(key, prop["fields"])
        }
    }

    fun componentAt(address: Any?): String? {
        val target = address as? Map<*, *> ?: return null
        val owner = target["owner"] as? String ?: return null
        val node = target["node"] as? String ?: return null
        return instances[owner to node]
    }

    fun binding(
        owner: String,
        value: Any?,
    ): DesignAssetProperty? {
        var ref = value as? Map<*, *> ?: return null
        val path = ArrayDeque<String>()
        val seen = mutableSetOf<String>()
        while ("repeat" in ref) {
            val repeat = ref["repeat"] as? String ?: return null
            if (!seen.add(repeat)) return null
            path.addFirst(ref["field"] as? String ?: return null)
            ref = repeats[owner to repeat] ?: return null
        }
        path.addFirst(ref["prop"] as? String ?: return null)
        return DesignAssetProperty(owner, path.toList())
    }

    fun values(
        owner: String,
        component: String?,
        values: Any?,
    ) {
        if (component == null || values !is Map<*, *>) return
        fields(owner, DesignAssetProperty(component, emptyList()), values)
    }

    fun change(
        owner: String,
        component: String?,
        change: Map<*, *>,
    ) {
        val prop = change["prop"] as? String ?: return
        if (component == null) return
        val nested = (change["within"] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()
        val parent = DesignAssetProperty(component, listOf(prop) + nested.mapNotNull { it["field"] as? String })
        when (change["op"]) {
            "input" -> value(owner, parent, change["value"])
            "itemSet" -> value(owner, parent.copy(path = parent.path + change["field"].toString()), change["value"])
            "itemInsert" -> row(owner, parent, change["item"])
        }
    }

    private fun fields(
        owner: String,
        parent: DesignAssetProperty,
        fields: Map<*, *>,
    ) {
        fields.forEach { (key, item) -> value(owner, parent.copy(path = parent.path + key.toString()), item) }
    }

    private fun row(
        owner: String,
        parent: DesignAssetProperty,
        item: Any?,
    ) {
        val fields = (item as? Map<*, *>)?.get("fields") as? Map<*, *> ?: return
        fields(owner, parent, fields)
    }

    private fun value(
        owner: String,
        target: DesignAssetProperty,
        item: Any?,
    ) {
        when (declarations[target]?.get("type")) {
            "asset" -> {
                types.input(target, item, binding(owner, item))
            }

            "list" -> {
                list(owner, target, item)
            }
        }
    }

    private fun list(
        owner: String,
        target: DesignAssetProperty,
        item: Any?,
    ) {
        if (item is List<*>) item.forEach { row(owner, target, it) } else binding(owner, item)?.let { forwardList(target, it) }
    }

    private fun forwardList(
        target: DesignAssetProperty,
        source: DesignAssetProperty,
    ) {
        val paths = declarations.filterValues { it["type"] == "asset" }.keys
        for (key in paths.filter { it.owner == target.owner && it.path.take(target.path.size) == target.path }) {
            types.input(key, null, source.copy(path = source.path + key.path.drop(target.path.size)))
        }
    }
}
