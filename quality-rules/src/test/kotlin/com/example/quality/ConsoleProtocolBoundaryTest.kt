package com.example.quality

import dev.detekt.api.Config
import dev.detekt.test.junit.KotlinCoreEnvironmentTest
import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.KotlinEnvironmentContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@KotlinCoreEnvironmentTest
class ConsoleProtocolBoundaryTest(
    private val environment: KotlinEnvironmentContainer,
) {
    private val rule = ConsoleProtocolBoundary(Config.empty)

    @Test
    fun `exact CLI protocol is allowed`() {
        assertFindings(
            console(
                """
                fun output(text: String) { System.out.println(text) }
                fun error(text: String) { System.err.println(text) }
                fun instruction(url: String) { System.err.println("Open " + url) }
                fun finish(status: Int): Nothing = kotlin.system.exitProcess(status)
                """.trimIndent(),
            ),
            0,
        )
    }

    @Test
    fun `server and other same named classes retain both bans`() {
        val packages = listOf("xyz.robinjoon.agentdocs.adapter.inbound.console", "other")
        packages.forEach { packageName ->
            val code = console("fun output(text: String) { System.out.println(text); kotlin.system.exitProcess(1) }")
            assertFindings(code.replace(CONSOLE_PACKAGE, packageName), 2)
        }
        assertFindings(console("fun output(text: String) { System.out.println(text) }").replace("object Console", "object Other"), 1)
    }

    @Test
    fun `other functions overloads and wrong streams cannot use protocol permission`() {
        val declarations =
            listOf(
                "fun other(text: String) { System.out.println(text) }",
                "fun output(text: Int) { System.out.println(text) }",
                "fun output(text: String?) { System.out.println(text) }",
                "fun <T> output(text: String) { System.out.println(text) }",
                "fun String.output(text: String) { System.out.println(text) }",
                "fun output(text: String, extra: Int) { System.out.println(text) }",
                "fun output(vararg text: String) { System.out.println(text.size) }",
                "fun output(text: String) { System.err.println(text) }",
                "fun error(text: String) { System.out.println(text) }",
                "fun instruction(text: String) { System.out.println(text) }",
                "fun output(text: String) { kotlin.system.exitProcess(1) }",
                "fun finish(status: String): Nothing = kotlin.system.exitProcess(1)",
                "fun finish(status: Int) { System.out.println(status) }",
            )
        declarations.forEach { assertFindings(console(it), 1) }
    }

    @Test
    fun `DSL lambda and local function bodies have no permission`() {
        val bodies =
            listOf(
                "fun dsl(block: () -> Unit) = block(); dsl { System.out.println(text) }",
                "run { System.out.println(text) }",
                "val action = { System.out.println(text) }; action()",
                "fun nested() { System.out.println(text) }; nested()",
                "with(System.out) { println(text) }",
            )
        bodies.forEach { assertFindings(console("fun output(text: String) { $it }"), 1) }
        assertFindings(console("fun finish(status: Int) { run { kotlin.system.exitProcess(status) } }"), 1)
    }

    @Test
    fun `different PrintStream receivers and aliases are forbidden even in output`() {
        val bodies =
            listOf(
                "val stream = System.out; stream.println(text)",
                "java.io.PrintStream(java.io.ByteArrayOutputStream()).println(text)",
            )
        bodies.forEach { assertFindings(console("fun output(text: String) { $it }"), 1) }
    }

    @Test
    fun `method references remain forbidden inside and outside protocol`() {
        assertFindings(
            console(
                """
                fun output(text: String) { val emit: (String) -> Unit = System.out::println; emit(text) }
                fun finish(status: Int) { val stop: (Int) -> Nothing = ::exitProcess; stop(status) }
                """.trimIndent(),
            ).replace("import java.lang.System", "import java.lang.System\nimport kotlin.system.exitProcess"),
            2,
        )
        assertFindings(
            """
            import kotlin.system.exitProcess
            val emit: (String) -> Unit = System.err::println
            val stop: (Int) -> Nothing = ::exitProcess
            """.trimIndent(),
            2,
        )
    }

    @Test
    fun `import aliases do not bypass server bans and genuine System alias is allowed`() {
        assertFindings(
            """
            import kotlin.system.exitProcess as stop
            import java.lang.System as Platform
            fun main() { Platform.out.println("bad"); stop(1) }
            """.trimIndent(),
            2,
        )
        assertFindings(
            console("fun output(text: String) { Platform.out.println(text) }")
                .replace("import java.lang.System", "import java.lang.System as Platform"),
            0,
        )
    }

    @Test
    fun `shadow System cannot grant stream permission`() {
        assertFindings(
            console(
                """
                object System { val out = java.lang.System.out }
                fun output(text: String) { System.out.println(text) }
                """.trimIndent(),
            ),
            1,
        )
    }

    @Test
    fun `PrintStream overrides retain the original forbidden method semantics`() {
        assertFindings(
            """
            class Stream : java.io.PrintStream(java.io.ByteArrayOutputStream()) {
                override fun println(text: String?) = Unit
            }
            fun operation() { Stream().println("bad") }
            """.trimIndent(),
            1,
        )
    }

    @Test
    fun `unrelated methods with same names are allowed`() {
        assertFindings(
            """
            class Sink {
                fun println(text: String) = text.length
                fun exitProcess(status: Int) = status
            }
            fun operation() { Sink().println("ok"); Sink().exitProcess(1) }
            """.trimIndent(),
            0,
        )
    }

    private fun console(declarations: String): String =
        """
        package $CONSOLE_PACKAGE
        import java.lang.System
        internal object Console {
            $declarations
        }
        """.trimIndent()

    private fun assertFindings(
        code: String,
        expected: Int,
    ) {
        assertEquals(expected, rule.lintWithContext(environment, code).size, code)
    }

    private companion object {
        const val CONSOLE_PACKAGE = "xyz.robinjoon.adocs.adapter.inbound.console"
    }
}
