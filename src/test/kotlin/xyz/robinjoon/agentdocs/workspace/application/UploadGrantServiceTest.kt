package xyz.robinjoon.agentdocs.workspace.application

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.agent
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.agentAccess
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.connectionId
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.context
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.now
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.owner
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.space
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures.spaceId
import xyz.robinjoon.agentdocs.workspace.application.model.UploadedFile
import xyz.robinjoon.agentdocs.workspace.application.port.input.AssetUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.PermissionDeniedException
import xyz.robinjoon.agentdocs.workspace.application.port.input.SpaceNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.UploadRejectedException
import xyz.robinjoon.agentdocs.workspace.application.port.output.SpaceRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.UploadGrantRepository
import xyz.robinjoon.agentdocs.workspace.application.port.output.UploadSecretSource
import xyz.robinjoon.agentdocs.workspace.application.service.UploadGrantService
import xyz.robinjoon.agentdocs.workspace.domain.Actor
import xyz.robinjoon.agentdocs.workspace.domain.AgentIdentity
import xyz.robinjoon.agentdocs.workspace.domain.Permission
import xyz.robinjoon.agentdocs.workspace.domain.asset.Asset
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetContent
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetMediaType
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetOrigin
import xyz.robinjoon.agentdocs.workspace.domain.asset.InvalidAssetException
import xyz.robinjoon.agentdocs.workspace.domain.upload.UploadGrant
import xyz.robinjoon.agentdocs.workspace.domain.upload.UploadGrantId
import xyz.robinjoon.agentdocs.workspace.domain.upload.UploadSecretHash
import xyz.robinjoon.agentdocs.workspace.domain.upload.UploadTarget
import java.time.Duration
import java.time.Instant
import java.util.UUID

class UploadGrantServiceTest {
    private val spaces = mockk<SpaceRepository>()
    private val grants = mockk<UploadGrantRepository>()
    private val secrets = mockk<UploadSecretSource>()
    private val assets = mockk<AssetUseCase>()
    private val service = UploadGrantService(spaces, grants, secrets, assets, WorkspaceFixtures.clock)
    private val editor = context(agentAccess(Permission.EDIT, setOf(spaceId)))
    private val hash = UploadSecretHash.of("secret")
    private val file = UploadedFile("flow.png", "image/png", byteArrayOf(1, 2, 3))
    private val asset =
        Asset.upload(
            AssetId(UUID.randomUUID()),
            spaceId,
            AssetContent.of(AssetMediaType.PNG, file.bytes),
            AssetOrigin("flow.png", null, agent, now),
        )

    init {
        every { spaces.findBySlug(space.slug) } returns space
        every { spaces.findById(spaceId) } returns space
        every { secrets.next() } returns "secret"
    }

    @Test
    fun `an agent that may edit the space gets a pass for ten minutes, and only the secret's hash is kept`() {
        every { grants.add(match { it.secretHash == hash && it.target == UploadTarget(spaceId, agent, "흐름") }) } answers { firstArg() }

        val pass = service.grant(editor, space.slug, "흐름")

        assertThat(pass.secret).isEqualTo("secret")
        assertThat(pass.connectionId).isEqualTo(connectionId)
        assertThat(pass.expiresAt).isEqualTo(now.plus(Duration.ofMinutes(10)))
    }

    @Test
    fun `an agent that may not edit the space gets no pass`() {
        assertThatThrownBy { service.grant(context(agentAccess(Permission.COMMENT, setOf(spaceId))), space.slug, "흐름") }
            .isInstanceOf(PermissionDeniedException::class.java)
    }

    @Test
    fun `a space the agent cannot see is not found`() {
        val outsider = context(agentAccess(Permission.EDIT, emptySet()))

        assertThatThrownBy { service.grant(outsider, space.slug, "흐름") }.isInstanceOf(SpaceNotFoundException::class.java)
    }

    @Test
    fun `a secret is admitted only for the connection its grant names`() {
        every { grants.findBySecretHash(hash) } returns grant()

        assertThat(service.admits(connectionId, "secret")).isTrue()
        assertThat(service.admits(UUID.randomUUID(), "secret")).isFalse()
    }

    @Test
    fun `an unknown secret is not admitted`() {
        every { grants.findBySecretHash(hash) } returns null

        assertThat(service.admits(connectionId, "secret")).isFalse()
    }

    @Test
    fun `a file sent with a good secret becomes an asset under the alt text the agent asked for`() {
        every { grants.findBySecretHashForUpdate(hash) } returns grant()
        every { assets.upload(editor, space.slug, file) } returns asset
        every { grants.update(match { it.term.usedAt == now }) } answers { firstArg() }

        val uploaded = service.upload(editor, "secret", file)

        assertThat(uploaded.asset).isEqualTo(asset)
        assertThat(uploaded.alt).isEqualTo("흐름")
    }

    @Test
    fun `an unknown secret is refused`() {
        every { grants.findBySecretHashForUpdate(hash) } returns null

        assertThatThrownBy { service.upload(editor, "secret", file) }.isInstanceOf(UploadRejectedException::class.java)
    }

    @Test
    fun `an expired grant is refused`() {
        every { grants.findBySecretHashForUpdate(hash) } returns grant(issuedAt = now.minus(Duration.ofMinutes(10)))

        assertThatThrownBy { service.upload(editor, "secret", file) }.isInstanceOf(UploadRejectedException::class.java)
    }

    @Test
    fun `a used grant is refused`() {
        every { grants.findBySecretHashForUpdate(hash) } returns grant().used(now.minusSeconds(1))

        assertThatThrownBy { service.upload(editor, "secret", file) }.isInstanceOf(UploadRejectedException::class.java)
    }

    @Test
    fun `a secret brought by another connection is refused`() {
        val other = Actor.agent(owner.userId, owner.login, AgentIdentity(UUID.randomUUID(), "Codex"))
        every { grants.findBySecretHashForUpdate(hash) } returns grant(issuer = other)

        assertThatThrownBy { service.upload(editor, "secret", file) }.isInstanceOf(UploadRejectedException::class.java)
    }

    @Test
    fun `a file that cannot be kept leaves the grant unused so the agent can send the right one`() {
        every { grants.findBySecretHashForUpdate(hash) } returns grant()
        every { assets.upload(editor, space.slug, file) } throws InvalidAssetException("only images")

        assertThatThrownBy { service.upload(editor, "secret", file) }.isInstanceOf(InvalidAssetException::class.java)
    }

    private fun grant(
        issuer: Actor = agent,
        issuedAt: Instant = now.minusSeconds(5),
    ): UploadGrant = UploadGrant.issue(UploadGrantId(UUID.randomUUID()), UploadTarget(spaceId, issuer, "흐름"), hash, issuedAt)
}
