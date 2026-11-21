package xyz.robinjoon.agentdocs.workspace.domain.document

import java.util.Collections

@ConsistentCopyVisibility
data class FlowAppearance private constructor(
    val kind: String,
    val look: String?,
    val items: List<String>?,
    val date: String?,
) {
    init {
        if (kind !in kinds || !validLook() || !validItems() || (date != null && (kind != "path" || date.length > 40))) {
            throw InvalidDocumentException("invalid flow appearance")
        }
    }

    private fun validLook(): Boolean = looks[kind]?.contains(look) ?: (look == null)

    private fun validItems(): Boolean =
        if (kind ==
            "list"
        ) {
            items != null && items.size <= 100 && items.all { it.length <= 200 }
        } else {
            items == null
        }

    fun visibleText(): List<String> = items.orEmpty() + listOfNotNull(date)

    fun replaceVisible(
        find: String,
        replacement: String,
    ): FlowAppearance =
        of(
            kind,
            look,
            items?.map { it.replace(find, replacement) },
            date?.replace(find, replacement),
        )

    companion object {
        private val kinds =
            setOf(
                "title",
                "text",
                "field",
                "select",
                "area",
                "button",
                "link",
                "list",
                "option",
                "notice",
                "path",
                "bar",
                "crumb",
                "block",
                "box",
                "image",
            )
        private val looks =
            mapOf(
                "box" to setOf("white", "sheet", "gray", "dim", "outline", "handle"),
                "title" to setOf("large", "small"),
                "button" to setOf("primary", "secondary", "disabled"),
                "notice" to setOf("info", "error", "ok"),
                "field" to setOf("normal", "inactive"),
                "select" to setOf("normal", "inactive"),
                "area" to setOf("normal", "inactive"),
            )

        private val defaultLooks =
            mapOf(
                "box" to "white",
                "title" to "large",
                "button" to "primary",
                "notice" to "info",
                "field" to "normal",
                "select" to "normal",
                "area" to "normal",
            )

        fun defaultLook(kind: String): String? = defaultLooks[kind]

        fun of(
            kind: String,
            look: String?,
            items: List<String>?,
            date: String?,
        ): FlowAppearance = FlowAppearance(kind, look, items?.let { Collections.unmodifiableList(it.toList()) }, date)
    }
}
