package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import xyz.robinjoon.agentdocs.workspace.domain.Actor
import xyz.robinjoon.agentdocs.workspace.domain.AgentIdentity
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.Block
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.ChangeSource
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentHeader
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentProperties
import xyz.robinjoon.agentdocs.workspace.domain.document.InlineText
import xyz.robinjoon.agentdocs.workspace.domain.document.Paragraph
import xyz.robinjoon.agentdocs.workspace.domain.document.history.ChangeOrigin
import xyz.robinjoon.agentdocs.workspace.domain.document.history.Revision
import xyz.robinjoon.agentdocs.workspace.domain.document.history.RevisionChange
import xyz.robinjoon.agentdocs.workspace.domain.space.Space
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceProfile
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import java.time.Instant
import java.util.UUID

// Values for persistence adapter tests. Times have microsecond precision, which both databases keep exactly.
object PersistenceFixtures {
    val at: Instant = Instant.parse("2026-10-18T09:00:00.123456Z")
    val person = Actor.human(UUID.fromString("11111111-1111-4111-8111-111111111111"), "robin")
    val agent = Actor.agent(person.userId, "robin", AgentIdentity(UUID.fromString("22222222-2222-4222-8222-222222222222"), "Claude Code"))

    fun space(slug: String): Space = Space.create(SpaceId(UUID.randomUUID()), SpaceSlug(slug), SpaceProfile.of(slug, "", emptyList()), at)

    fun paragraph(
        id: String,
        text: String,
    ): Paragraph = Paragraph(BlockId(id), InlineText(text))

    fun document(
        space: SpaceId,
        path: String,
        blocks: List<Block>,
    ): Document =
        Document.create(
            DocumentId(UUID.randomUUID()),
            DocumentHeader(space, DocumentPath(path), "Title of $path", DocumentProperties.initial),
            blocks,
            at,
        )

    fun revisionOf(
        document: Document,
        actor: Actor = person,
        changed: List<BlockId> = document.blocks.map { it.id },
    ): Revision =
        Revision.of(document, RevisionChange.of(ChangeOrigin(actor, ChangeSource.WEB), "블록 수정", changed, document.lifecycle.updatedAt))
}
