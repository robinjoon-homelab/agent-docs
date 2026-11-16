package xyz.robinjoon.agentdocs.access.application.model

import java.io.Serializable
import java.util.Collections
import java.util.UUID

// Who is making a request, as other contexts receive it. A person signed in through GitHub has no agent; the owner
// sees every space (spaces null). An agent carries its connection and sees exactly the spaces it was granted. It rides
// in an Authentication, which Spring Security requires to be serializable.
class Caller(
    val userId: UUID,
    val login: String,
    val owner: Boolean,
    val agent: CallerAgent?,
    spaces: Map<UUID, CallerScope>?,
    val scope: CallerScope,
) : Serializable {
    val spaces: Map<UUID, CallerScope>? = spaces?.let { Collections.unmodifiableMap(LinkedHashMap(it)) }

    init {
        require(owner == (scope == CallerScope.OWNER)) { "only the owner holds OWNER" }
        require(!owner || agent == null) { "only a person can be the owner" }
        require((this.spaces == null) == owner) { "only a person owner sees all spaces" }
        require(this.spaces?.values?.all { it != CallerScope.OWNER && it <= scope } != false) {
            "space grants must stay within the non-owner scope ceiling"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Caller) return false
        return userId == other.userId && login == other.login && owner == other.owner &&
            agent == other.agent && spaces == other.spaces && scope == other.scope
    }

    override fun hashCode(): Int {
        var result = userId.hashCode()
        result = 31 * result + login.hashCode()
        result = 31 * result + owner.hashCode()
        result = 31 * result + (agent?.hashCode() ?: 0)
        result = 31 * result + (spaces?.hashCode() ?: 0)
        return 31 * result + scope.hashCode()
    }
}
