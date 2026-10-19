package xyz.robinjoon.agentdocs.workspace.application

import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.domain.Access
import xyz.robinjoon.agentdocs.workspace.domain.Actor
import xyz.robinjoon.agentdocs.workspace.domain.AgentIdentity
import xyz.robinjoon.agentdocs.workspace.domain.Permission
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.SpaceScope
import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.ChangeSource
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentHeader
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentLifecycle
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentProperties
import xyz.robinjoon.agentdocs.workspace.domain.document.InlineText
import xyz.robinjoon.agentdocs.workspace.domain.document.Paragraph
import xyz.robinjoon.agentdocs.workspace.domain.document.history.ChangeOrigin
import xyz.robinjoon.agentdocs.workspace.domain.document.history.ChangePeriod
import xyz.robinjoon.agentdocs.workspace.domain.document.history.Revision
import xyz.robinjoon.agentdocs.workspace.domain.document.history.RevisionChange
import xyz.robinjoon.agentdocs.workspace.domain.document.history.RevisionContent
import xyz.robinjoon.agentdocs.workspace.domain.space.Space
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceProfile
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

// Shared values for use case tests: one owner, one agent connection of that owner, one space, one document.
object WorkspaceFixtures {
    val now: Instant = Instant.parse("2026-10-18T09:00:00Z")
    val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    val ownerId: UUID = UUID.fromString("11111111-1111-4111-8111-111111111111")
    val connectionId: UUID = UUID.fromString("22222222-2222-4222-8222-222222222222")
    val owner = Actor.human(ownerId, "robin")
    val agent = Actor.agent(ownerId, "robin", AgentIdentity(connectionId, "Claude Code"))
    val ownerAccess = Access(owner, SpaceScope.AllSpaces, Permission.OWNER)
    val spaceId = SpaceId(UUID.fromString("33333333-3333-4333-8333-333333333333"))
    val otherSpaceId = SpaceId(UUID.fromString("44444444-4444-4444-8444-444444444444"))
    val space = Space.create(spaceId, SpaceSlug("agent-docs"), SpaceProfile.of("Agent Docs", "", emptyList()), now.minusSeconds(86_400))
    val documentId = DocumentId(UUID.fromString("55555555-5555-4555-8555-555555555555"))
    val header = DocumentHeader(spaceId, DocumentPath("payments/retry"), "Retry design", DocumentProperties.initial)

    fun agentAccess(
        permission: Permission,
        spaces: Set<SpaceId>,
    ): Access = Access(agent, SpaceScope.Listed(spaces), permission)

    fun context(
        access: Access,
        source: ChangeSource = ChangeSource.WEB,
        operationId: String? = null,
    ): WriteContext = WriteContext(access, source, operationId, null)

    fun paragraph(
        id: String,
        text: String,
    ): Paragraph = Paragraph(BlockId(id), InlineText(text))

    fun document(
        blocks: List<Block>,
        revision: Int,
        trashedAt: Instant? = null,
    ): Document =
        Document.restore(documentId, header, blocks, DocumentLifecycle(revision, now.minusSeconds(3_600), now.minusSeconds(60), trashedAt))

    fun revision(
        number: Int,
        blocks: List<Block>,
        origin: ChangeOrigin,
        updatedAt: Instant,
    ): Revision =
        Revision(
            documentId,
            number,
            RevisionContent.of(header, blocks, false),
            RevisionChange.restore(origin, "블록 1개 수정", blocks.map { it.id }, ChangePeriod(updatedAt, updatedAt)),
        )
}
