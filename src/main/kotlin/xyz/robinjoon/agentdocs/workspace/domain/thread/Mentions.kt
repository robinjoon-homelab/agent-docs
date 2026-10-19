package xyz.robinjoon.agentdocs.workspace.domain.thread

import java.util.Collections

// The GitHub logins a comment mentions as @login. Code spans and fenced code are skipped, so pasted code with an
// annotation such as @Test does not notify anyone.
object Mentions {
    private val code = Regex("```[\\s\\S]*?```|`[^`\\n]*`")
    private val mention = Regex("(?<![A-Za-z0-9_@./])@([A-Za-z0-9](?:[A-Za-z0-9-]{0,38}))")

    fun of(body: String): Set<String> =
        Collections.unmodifiableSet(
            mention
                .findAll(body.replace(code, " "))
                .map { it.groupValues[1].lowercase() }
                .toCollection(LinkedHashSet()),
        )
}
