package xyz.robinjoon.agentdocs.workspace.domain.document.design

/** List edits address retained row keys, including nested lists reached through a row field. */
internal class DesignRows(
    private val change: DesignFields,
    private val resolve: (Any?) -> Any?,
) {
    fun apply(
        rows: List<DesignFields>,
        within: List<DesignFields> = change["within"].objects(),
    ): List<DesignFields> {
        val hop = within.firstOrNull() ?: return edit(rows)
        val field = hop["field"] as? String ?: return rows
        return rows.map { row ->
            if (row["itemKey"] != hop["itemKey"]) {
                row
            } else {
                val fields = row["fields"].stringFields()
                row + ("fields" to (fields + (field to apply(fields[field].objects(), within.drop(1)))))
            }
        }
    }

    private fun edit(rows: List<DesignFields>): List<DesignFields> {
        if (change["op"] == "itemInsert") {
            val item = resolve(listOf(change["item"])).objects().singleOrNull() ?: return rows
            if (rows.any { it["itemKey"] == item["itemKey"] }) return rows
            return place(rows, item) ?: rows
        }
        val row = rows.find { it["itemKey"] == change["itemKey"] } ?: return rows
        return when (change["op"]) {
            "itemMove" -> {
                place(rows - listOf(row), row) ?: rows
            }

            "itemRemove" -> {
                rows - listOf(row)
            }

            "itemSet" -> {
                val fields = row["fields"].stringFields() + (change["field"].toString() to resolve(change["value"]))
                rows.map { if (it === row) row + ("fields" to fields) else it }
            }

            else -> {
                rows
            }
        }
    }

    private fun place(
        rows: List<DesignFields>,
        item: DesignFields,
    ): List<DesignFields>? {
        val at = change["at"] as? DesignFields ?: return null
        val adjacent = at["before"] ?: at["after"]
        val index =
            if (adjacent == null) {
                if (at["edge"] == "first") 0 else rows.size
            } else {
                val sibling = rows.indexOfFirst { it["itemKey"] == adjacent }
                if (sibling < 0) return null
                sibling + if ("after" in at) 1 else 0
            }
        return rows.take(index) + listOf(item) + rows.drop(index)
    }
}
