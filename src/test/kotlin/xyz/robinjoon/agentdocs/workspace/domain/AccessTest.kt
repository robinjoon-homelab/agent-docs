package xyz.robinjoon.agentdocs.workspace.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class AccessTest {
    private val actor = Actor.human(UUID.randomUUID(), "member")
    private val edited = SpaceId(UUID.randomUUID())
    private val commented = SpaceId(UUID.randomUUID())

    @Test
    fun `each space uses its own grant and unseen spaces have no permission`() {
        val access = Access(actor, SpaceScope.Listed(mapOf(edited to Permission.EDIT, commented to Permission.COMMENT)), Permission.EDIT)
        val missing = SpaceId(UUID.randomUUID())

        assertThat(access.permissionIn(edited)).isEqualTo(Permission.EDIT)
        assertThat(access.allows(edited, Permission.EDIT)).isTrue()
        assertThat(access.permissionIn(commented)).isEqualTo(Permission.COMMENT)
        assertThat(access.allows(commented, Permission.COMMENT)).isTrue()
        assertThat(access.allows(commented, Permission.EDIT)).isFalse()
        assertThat(access.permissionIn(missing)).isNull()
        assertThat(access.sees(missing)).isFalse()
        assertThat(access.allows(missing, Permission.READ)).isFalse()
        assertThat(access.isOwner).isFalse()
    }

    @Test
    fun `the global ceiling still limits listed grants`() {
        val access = Access(actor, SpaceScope.Listed(mapOf(edited to Permission.EDIT)), Permission.READ)

        assertThat(access.permissionIn(edited)).isEqualTo(Permission.READ)
        assertThat(access.allows(edited, Permission.COMMENT)).isFalse()
    }

    @Test
    fun `only a human owner may hold global owner permission and all spaces`() {
        val owner = Access(actor, SpaceScope.AllSpaces, Permission.OWNER)
        assertThat(owner.permissionIn(edited)).isEqualTo(Permission.OWNER)
        assertThat(owner.allows(edited, Permission.OWNER)).isTrue()
        assertThat(owner.isOwner).isTrue()
        assertThatThrownBy { SpaceScope.Listed(mapOf(edited to Permission.OWNER)) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { Access(actor, SpaceScope.Listed(emptyMap()), Permission.OWNER) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { Access(actor, SpaceScope.AllSpaces, Permission.EDIT) }.isInstanceOf(IllegalArgumentException::class.java)
        val agent = Actor.agent(actor.userId, actor.login, AgentIdentity(UUID.randomUUID(), "tool"))
        assertThatThrownBy { Access(agent, SpaceScope.AllSpaces, Permission.OWNER) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `listed grants and computed keys cannot be changed through shared maps`() {
        val input = mutableMapOf(edited to Permission.EDIT)
        val listed = SpaceScope.Listed(input)
        input.clear()

        assertThat(listed.grants).containsExactlyEntriesOf(mapOf(edited to Permission.EDIT))
        assertThat(listed.spaces).containsExactly(edited)
        assertThat(listed.grants).isUnmodifiable()
        assertThat(listed.spaces).isUnmodifiable()
    }

    @Test
    fun `listed grants restore value equality and hashing for access arguments`() {
        val listed = SpaceScope.Listed(mapOf(edited to Permission.EDIT, commented to Permission.COMMENT))
        val equal = SpaceScope.Listed(mapOf(commented to Permission.COMMENT, edited to Permission.EDIT))

        assertThat(listed).isEqualTo(equal)
        assertThat(equal).isEqualTo(listed)
        assertThat(listed.hashCode()).isEqualTo(equal.hashCode())
        assertThat(setOf(listed, equal)).hasSize(1)
        assertThat(listed).isNotEqualTo(null).isNotEqualTo(SpaceScope.AllSpaces)
        assertThat(listed).isNotEqualTo(SpaceScope.Listed(mapOf(edited to Permission.COMMENT, commented to Permission.COMMENT)))
        assertThat(listed).isNotEqualTo(SpaceScope.Listed(mapOf(edited to Permission.EDIT)))
        val access = Access(actor, listed, Permission.EDIT)
        val equalAccess = Access(actor.copy(), equal, Permission.EDIT)
        assertThat(access).isEqualTo(equalAccess)
        assertThat(access.hashCode()).isEqualTo(equalAccess.hashCode())
        assertThat(mapOf(access to "cached")[equalAccess]).isEqualTo("cached")
    }
}
