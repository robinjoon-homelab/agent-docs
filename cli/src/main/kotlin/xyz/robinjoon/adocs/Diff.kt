package xyz.robinjoon.adocs

internal object Diff {
    fun operations(
        original: String,
        edited: String,
    ): List<Map<String, Any>> {
        validateHeader(original, edited)
        val old = Markdown.fragments(original)
        val next = Markdown.fragments(edited)
        val byId = old.associateBy(Fragment::id)
        validateAnnotations(old, next)
        val operations = arrayListOf<Map<String, Any>>()
        val removed = old.filter { before -> next.none { it.id == before.id } }
        removed.forEach { operations += guarded("delete", it) }
        val order = old.filterNot { it in removed }.mapNotNull(Fragment::id).toMutableList()
        next.filter { it.id != null }.forEachIndexed { index, fragment ->
            val before = requireNotNull(byId[fragment.id])
            if (fragment.markdown != before.markdown) operations += guarded("replace", before) + ("markdown" to fragment.rendered())
            val id = requireNotNull(fragment.id)
            if (order.indexOf(id) != index) {
                operations += guarded("move", before) + position(order.getOrNull(index - 1))
                order.remove(id)
                order.add(index, id)
            }
        }
        inserts(next).forEach { operations += it }
        if (operations.size >
            200
        ) {
            throw CliFailure("too_many_operations", "More than 200 operations; split local changes into separate pushes")
        }
        return operations
    }

    private fun validateHeader(
        original: String,
        edited: String,
    ) {
        if (Markdown.header(original) != Markdown.header(edited)) {
            throw CliFailure("header_changed", "Use adocs header DOCUMENT --body FILE for header changes")
        }
    }

    private fun validateAnnotations(
        old: List<Fragment>,
        next: List<Fragment>,
    ) {
        val byId = old.associateBy(Fragment::id)
        next.filter { it.id != null }.forEach {
            require(byId[it.id]?.annotation == it.annotation) { "block annotation changed or not present in pull baseline" }
        }
        val missing = old.filter { before -> next.none { it.id == before.id } }
        require(missing.none { before -> next.any { it.id == null && it.markdown == before.markdown } }) {
            "block annotation removed; restore it, or delete the entire block"
        }
    }

    private fun inserts(next: List<Fragment>): List<Map<String, Any>> {
        val operations = arrayListOf<Map<String, Any>>()
        var index = 0
        while (index < next.size) {
            if (next[index].id != null) {
                index++
                continue
            }
            val start = index
            while (index < next.size && next[index].id == null) index++
            val markdown = next.subList(start, index).joinToString("\n\n", transform = Fragment::rendered)
            val anchor = next.getOrNull(index)?.id
            val position = if (anchor == null) mapOf("at" to "end") else mapOf("before" to anchor)
            operations += mapOf("op" to "insert", "markdown" to markdown) + position
        }
        return operations
    }

    private fun guarded(
        op: String,
        fragment: Fragment,
    ): Map<String, Any> = mapOf("op" to op, "blockId" to requireNotNull(fragment.id), "ifHash" to requireNotNull(fragment.hash))

    private fun position(previous: String?): Map<String, Any> = if (previous == null) mapOf("at" to "start") else mapOf("after" to previous)
}
