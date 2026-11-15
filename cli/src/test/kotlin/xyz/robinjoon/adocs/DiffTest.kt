package xyz.robinjoon.adocs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiffTest {
    private val first = "<!-- b:aaaa h:hasha -->\nFirst"
    private val second = "<!-- b:bbbb h:hashb -->\nSecond"

    @Test
    fun `nested list text replacement preserves its indentation`() {
        val original = "<!-- b:aaaa h:hasha -->\n  - Child"
        val result = Diff.operations(original, original.replace("Child", "Changed")).single()
        assertEquals("replace", result["op"])
        assertEquals("<!-- b:aaaa h:hasha -->\n  - Changed", result["markdown"])
    }

    @Test
    fun `indentation changes alone produce a guarded replacement`() {
        val original = "<!-- b:aaaa h:hasha -->\n  - Child"
        val result = Diff.operations(original, original.replace("  -", "    -")).single()
        assertEquals("<!-- b:aaaa h:hasha -->\n    - Child", result["markdown"])
        assertEquals("hasha", result["ifHash"])
    }

    @Test
    fun `unannotated first list fragment preserves indentation after boundary blank lines`() {
        val result = Diff.operations("", "\n\n  - Child\n\n").single()
        assertEquals("  - Child", result["markdown"])
    }

    @Test
    fun `unchanged content produces no operations`() {
        assertTrue(Diff.operations("$first\n\n$second", "$first\n\n$second").isEmpty())
    }

    @Test
    fun `replace and move use baseline hash and delete only removed blocks`() {
        val changed = second.replace("Second", "Changed")
        val operations = Diff.operations("$first\n\n$second", "$changed\n\n$first")
        assertEquals(listOf("replace", "move"), operations.map { it["op"] })
        assertEquals("hashb", operations.first()["ifHash"])
        assertEquals("start", operations.last()["at"])
        assertEquals(listOf(mapOf("op" to "delete", "blockId" to "bbbb", "ifHash" to "hashb")), Diff.operations("$first\n\n$second", first))
    }

    @Test
    fun `new markdown is inserted as meaningful fragments before its existing anchor`() {
        val operations = Diff.operations(first, "# New heading\n\nA paragraph\n\n$first")
        assertEquals(1, operations.size)
        assertEquals("insert", operations.single()["op"])
        assertEquals("aaaa", operations.single()["before"])
        assertEquals("# New heading\n\nA paragraph", operations.single()["markdown"])
    }

    @Test
    fun `code annotations stay inside code and table row and column ids survive`() {
        val table = "<!-- b:cccc h:hashc cols:col1,col2 -->\n| <!--r:row1-->A | B |\n| --- | --- |\n| <!--r:row2-->C | D |"
        val code = "<!-- b:dddd h:hashd -->\n```html\n<!-- b:eeee h:fake -->\n```"
        assertTrue(Diff.operations("$table\n\n$code", "$table\n\n$code").isEmpty())
        val replacement = Diff.operations(table, table.replace("C |", "Changed |"))
        assertTrue(replacement.single()["markdown"].toString().contains("cols:col1,col2"))
        assertTrue(replacement.single()["markdown"].toString().contains("<!--r:row2-->"))
    }

    @Test
    fun `header changes annotation corruption and lost annotation are rejected`() {
        assertThrows(CliFailure::class.java) { Diff.operations("---\ntitle: One\n---\n\n$first", "---\ntitle: Two\n---\n\n$first") }
        assertThrows(IllegalArgumentException::class.java) { Diff.operations(first, first.replace("hasha", "changed")) }
        assertThrows(IllegalArgumentException::class.java) { Diff.operations(first, "First") }
        assertThrows(IllegalArgumentException::class.java) { Diff.operations(first, "$first\n\n$first") }
    }

    @Test
    fun `operation ceiling is enforced before a request can be formed`() {
        val original = (1000..1200).joinToString("\n\n") { "<!-- b:b$it h:h$it -->\nText $it" }
        val failure = assertThrows(CliFailure::class.java) { Diff.operations(original, "") }
        assertEquals("too_many_operations", failure.code)
    }
}
