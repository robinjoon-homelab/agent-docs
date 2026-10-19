package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.jetbrains.exposed.v1.core.LikePattern

// User text inside a LIKE pattern, with % and _ matched literally.
internal object LikePatterns {
    private const val ESCAPE = '\\'

    fun containing(text: String): LikePattern = LikePattern("%" + escape(text) + "%", ESCAPE)

    fun startingWith(text: String): LikePattern = LikePattern(escape(text) + "%", ESCAPE)

    private fun escape(text: String): String = text.replace("$ESCAPE", "$ESCAPE$ESCAPE").replace("%", "$ESCAPE%").replace("_", "${ESCAPE}_")
}
