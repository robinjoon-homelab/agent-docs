package xyz.robinjoon.agentdocs.access.domain.connection

// What an agent may do in its allowed spaces, from the narrowest. Each step includes the ones before it.
enum class ConnectionScope {
    READ,
    COMMENT,
    EDIT,
    ;

    val wire: String get() = name.lowercase()

    companion object {
        fun of(value: String): ConnectionScope? = entries.firstOrNull { it.wire == value.trim().lowercase() }
    }
}
