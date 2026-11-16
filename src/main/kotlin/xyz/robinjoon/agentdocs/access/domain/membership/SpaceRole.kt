package xyz.robinjoon.agentdocs.access.domain.membership

// What an invited person may do in one space. An editor writes documents; a commenter reads and comments. Neither ever
// holds what only the owner does.
enum class SpaceRole {
    EDITOR,
    COMMENTER,
    ;

    val wire: String get() = name.lowercase()

    companion object {
        fun of(value: String): SpaceRole? = entries.firstOrNull { it.wire == value.trim().lowercase() }
    }
}
