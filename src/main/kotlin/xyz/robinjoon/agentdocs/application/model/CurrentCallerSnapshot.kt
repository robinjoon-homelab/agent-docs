package xyz.robinjoon.agentdocs.application.model

import java.util.Collections
import java.util.UUID

class CurrentCallerSnapshot(
    val userId: UUID,
    val login: String,
    val connection: Connection?,
    spaces: Map<UUID, Scope>?,
    val scope: Scope,
) {
    val spaces: Map<UUID, Scope>? = spaces?.let { Collections.unmodifiableMap(LinkedHashMap(it)) }

    init {
        require((this.spaces == null) == (connection == null && scope == Scope.OWNER)) {
            "only a person owner sees all spaces and holds OWNER"
        }
        require(scope != Scope.OWNER || connection == null) { "an agent cannot hold OWNER" }
        require(this.spaces?.values?.all { it != Scope.OWNER && it <= scope } != false) {
            "space grants must stay within the non-owner scope ceiling"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CurrentCallerSnapshot) return false
        return userId == other.userId && login == other.login && connection == other.connection &&
            spaces == other.spaces && scope == other.scope
    }

    override fun hashCode(): Int {
        var result = userId.hashCode()
        result = 31 * result + login.hashCode()
        result = 31 * result + (connection?.hashCode() ?: 0)
        result = 31 * result + (spaces?.hashCode() ?: 0)
        return 31 * result + scope.hashCode()
    }

    data class Connection(
        val id: UUID,
        val name: String,
    )

    enum class Scope { READ, COMMENT, EDIT, OWNER }
}
