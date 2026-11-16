package xyz.robinjoon.agentdocs.access.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.access.application.model.Caller
import xyz.robinjoon.agentdocs.access.application.model.CallerAgent
import xyz.robinjoon.agentdocs.access.application.model.CallerScope
import xyz.robinjoon.agentdocs.application.model.CurrentCallerSnapshot
import java.util.UUID

class CallerTest {
    private val user = UUID.randomUUID()
    private val space = UUID.randomUUID()

    @Test
    fun `caller owns an immutable copy of its grants`() {
        val grants = mutableMapOf(space to CallerScope.EDIT)
        val caller = Caller(user, "member", false, null, grants, CallerScope.EDIT)
        grants.clear()

        assertThat(caller.spaces).containsExactlyEntriesOf(mapOf(space to CallerScope.EDIT))
        assertThat(caller.owner).isFalse()
        assertThat(caller.spaces).isUnmodifiable()
    }

    @Test
    fun `member and agent cannot claim owner or unbounded spaces`() {
        assertThatThrownBy { Caller(user, "member", false, null, null, CallerScope.READ) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { Caller(user, "member", false, null, emptyMap(), CallerScope.OWNER) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { Caller(user, "member", false, null, mapOf(space to CallerScope.OWNER), CallerScope.EDIT) }
            .isInstanceOf(IllegalArgumentException::class.java)
        val agent = CallerAgent(UUID.randomUUID(), "tool")
        assertThatThrownBy { Caller(user, "owner", false, agent, emptyMap(), CallerScope.OWNER) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { Caller(user, "owner", true, agent, null, CallerScope.OWNER) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { Caller(user, "member", false, null, mapOf(space to CallerScope.EDIT), CallerScope.READ) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `snapshot owns immutable grants and rejects owner promotion`() {
        val grants = mutableMapOf(space to CurrentCallerSnapshot.Scope.COMMENT)
        val snapshot = CurrentCallerSnapshot(user, "member", null, grants, CurrentCallerSnapshot.Scope.EDIT)
        grants.clear()

        assertThat(snapshot.spaces).containsExactlyEntriesOf(mapOf(space to CurrentCallerSnapshot.Scope.COMMENT))
        assertThat(snapshot.spaces).isUnmodifiable()
        assertThatThrownBy { CurrentCallerSnapshot(user, "member", null, emptyMap(), CurrentCallerSnapshot.Scope.OWNER) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy {
            CurrentCallerSnapshot(user, "member", null, mapOf(space to CurrentCallerSnapshot.Scope.OWNER), CurrentCallerSnapshot.Scope.EDIT)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `caller equality and hashing compare every field and unordered grant values`() {
        val otherSpace = UUID.randomUUID()
        val agent = CallerAgent(UUID.randomUUID(), "tool")
        val grants = mapOf(space to CallerScope.EDIT, otherSpace to CallerScope.COMMENT)
        val caller = Caller(user, "member", false, agent, grants, CallerScope.EDIT)
        val equal = Caller(user, "member", false, agent.copy(), grants.entries.reversed().associate { it.toPair() }, CallerScope.EDIT)

        assertThat(caller).isEqualTo(equal)
        assertThat(equal).isEqualTo(caller)
        assertThat(caller.hashCode()).isEqualTo(equal.hashCode())
        assertThat(setOf(caller, equal)).hasSize(1)
        assertThat(caller).isNotEqualTo(null).isNotEqualTo("member")
        assertThat(caller).isNotEqualTo(Caller(UUID.randomUUID(), "member", false, agent, grants, CallerScope.EDIT))
        assertThat(caller).isNotEqualTo(Caller(user, "renamed", false, agent, grants, CallerScope.EDIT))
        assertThat(caller).isNotEqualTo(Caller(user, "member", false, agent.copy(clientName = "other"), grants, CallerScope.EDIT))
        assertThat(caller).isNotEqualTo(Caller(user, "member", false, null, grants, CallerScope.EDIT))
        assertThat(caller).isNotEqualTo(Caller(user, "member", false, agent, mapOf(space to CallerScope.COMMENT), CallerScope.EDIT))
        assertThat(caller).isNotEqualTo(Caller(user, "member", false, agent, emptyMap(), CallerScope.READ))
        assertThat(caller).isNotEqualTo(Caller(user, "member", true, null, null, CallerScope.OWNER))
        val owner = Caller(user, "owner", true, null, null, CallerScope.OWNER)
        val equalOwner = Caller(user, "owner", true, null, null, CallerScope.OWNER)
        assertThat(owner).isEqualTo(equalOwner)
        assertThat(owner.hashCode()).isEqualTo(equalOwner.hashCode())
    }

    @Test
    fun `snapshot equality and hashing compare every field and unordered grant values`() {
        val otherSpace = UUID.randomUUID()
        val connection = CurrentCallerSnapshot.Connection(UUID.randomUUID(), "tool")
        val grants = mapOf(space to CurrentCallerSnapshot.Scope.COMMENT, otherSpace to CurrentCallerSnapshot.Scope.READ)
        val snapshot = CurrentCallerSnapshot(user, "member", connection, grants, CurrentCallerSnapshot.Scope.EDIT)
        val equal =
            CurrentCallerSnapshot(
                user,
                "member",
                connection.copy(),
                grants.entries.reversed().associate { it.toPair() },
                CurrentCallerSnapshot.Scope.EDIT,
            )

        assertThat(snapshot).isEqualTo(equal)
        assertThat(equal).isEqualTo(snapshot)
        assertThat(snapshot.hashCode()).isEqualTo(equal.hashCode())
        assertThat(setOf(snapshot, equal)).hasSize(1)
        assertThat(snapshot).isNotEqualTo(null).isNotEqualTo("member")
        assertThat(snapshot).isNotEqualTo(
            CurrentCallerSnapshot(
                UUID.randomUUID(),
                "member",
                connection,
                grants,
                CurrentCallerSnapshot.Scope.EDIT,
            ),
        )
        assertThat(snapshot).isNotEqualTo(CurrentCallerSnapshot(user, "renamed", connection, grants, CurrentCallerSnapshot.Scope.EDIT))
        assertThat(snapshot).isNotEqualTo(
            CurrentCallerSnapshot(
                user,
                "member",
                connection.copy(name = "other"),
                grants,
                CurrentCallerSnapshot.Scope.EDIT,
            ),
        )
        assertThat(snapshot).isNotEqualTo(CurrentCallerSnapshot(user, "member", null, grants, CurrentCallerSnapshot.Scope.EDIT))
        assertThat(snapshot).isNotEqualTo(CurrentCallerSnapshot(user, "member", connection, emptyMap(), CurrentCallerSnapshot.Scope.EDIT))
        assertThat(snapshot).isNotEqualTo(CurrentCallerSnapshot(user, "member", connection, grants, CurrentCallerSnapshot.Scope.COMMENT))
        val owner = CurrentCallerSnapshot(user, "owner", null, null, CurrentCallerSnapshot.Scope.OWNER)
        val equalOwner = CurrentCallerSnapshot(user, "owner", null, null, CurrentCallerSnapshot.Scope.OWNER)
        assertThat(owner).isEqualTo(equalOwner)
        assertThat(owner.hashCode()).isEqualTo(equalOwner.hashCode())
    }
}
