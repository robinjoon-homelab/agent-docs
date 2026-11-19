package xyz.robinjoon.agentdocs.workspace.application.port.output

fun interface MarkdownTokenCounter {
    fun count(text: String): Int
}
