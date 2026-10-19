package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath
import xyz.robinjoon.agentdocs.access.adapter.inbound.web.CallerAuthentication
import xyz.robinjoon.agentdocs.access.application.model.Caller
import xyz.robinjoon.agentdocs.access.application.model.CallerAgent
import xyz.robinjoon.agentdocs.access.application.model.CallerScope
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
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentStatus
import xyz.robinjoon.agentdocs.workspace.domain.document.Heading
import xyz.robinjoon.agentdocs.workspace.domain.document.InlineText
import xyz.robinjoon.agentdocs.workspace.domain.document.Paragraph
import xyz.robinjoon.agentdocs.workspace.domain.space.ExternalLink
import xyz.robinjoon.agentdocs.workspace.domain.space.ExternalLinkKind
import xyz.robinjoon.agentdocs.workspace.domain.space.Space
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceProfile
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import java.time.Instant
import java.util.UUID

// Shared values for the workspace HTTP tests: the signed-in owner, one agent connection, one space, one document.
object WebFixtures {
    val at: Instant = Instant.parse("2026-10-18T09:00:00Z")
    val userId: UUID = UUID.fromString("11111111-1111-4111-8111-111111111111")
    val connectionId: UUID = UUID.fromString("22222222-2222-4222-8222-222222222222")
    val ownerCaller = Caller(userId, "robinjoon", true, null, null, CallerScope.OWNER)
    val owner = CallerAuthentication(ownerCaller)
    val agentCaller =
        Caller(
            userId,
            "robinjoon",
            false,
            CallerAgent(connectionId, "Claude Code"),
            setOf(UUID.fromString("33333333-3333-4333-8333-333333333333")),
            CallerScope.EDIT,
        )
    val agent = CallerAuthentication(agentCaller)
    val ownerAccess = Access(Actor.human(userId, "robinjoon"), SpaceScope.AllSpaces, Permission.OWNER)
    val agentActor = Actor.agent(userId, "robinjoon", AgentIdentity(connectionId, "Claude Code"))
    val agentAccess =
        Access(agentActor, SpaceScope.Listed(setOf(SpaceId(UUID.fromString("33333333-3333-4333-8333-333333333333")))), Permission.EDIT)
    val webContext = WriteContext(ownerAccess, ChangeSource.WEB, null, null)
    val spaceId = SpaceId(UUID.fromString("33333333-3333-4333-8333-333333333333"))
    val space =
        Space.create(
            spaceId,
            SpaceSlug("agent-docs"),
            SpaceProfile.of("Agent Docs", "기획과 설계 문서", listOf(ExternalLink.of(ExternalLinkKind.GITHUB_REPOSITORY, "homelab/agent-docs"))),
            at.minusSeconds(86_400),
        )
    val documentId = DocumentId(UUID.fromString("55555555-5555-4555-8555-555555555555"))
    val title = Heading(BlockId("head01"), 1, InlineText("재시도 설계"))
    val body = Paragraph(BlockId("para01"), InlineText("실패하면 **30초** 뒤에 다시 시도한다."))
    val properties = DocumentProperties.of(listOf("payments"), DocumentStatus.IN_REVIEW, "robinjoon")

    fun document(
        blocks: List<Block> = listOf(title, body),
        revision: Int = 3,
    ): Document =
        Document.restore(
            documentId,
            DocumentHeader(spaceId, DocumentPath("payments/retry"), "재시도 설계", properties),
            blocks,
            DocumentLifecycle(revision, at.minusSeconds(3_600), at, null),
        )

    fun actorFields(prefix: String): Array<FieldDescriptor> =
        arrayOf(
            fieldWithPath("$prefix.kind").type(JsonFieldType.STRING).description("human 또는 agent"),
            fieldWithPath("$prefix.login").type(JsonFieldType.STRING).description("사람의 GitHub 로그인. 에이전트는 위임한 사람의 로그인"),
            fieldWithPath(
                "$prefix.agent",
            ).type(JsonFieldType.OBJECT).optional().description("에이전트일 때 연결 ID(connectionId)와 클라이언트 이름(clientName). 사람이면 null"),
            fieldWithPath("$prefix.agent.connectionId").type(JsonFieldType.STRING).optional().description("에이전트 연결 ID"),
            fieldWithPath("$prefix.agent.clientName").type(JsonFieldType.STRING).optional().description("에이전트 클라이언트 이름"),
        )

    fun spaceRefFields(prefix: String): Array<FieldDescriptor> =
        arrayOf(
            fieldWithPath("$prefix.id").description("스페이스 ID"),
            fieldWithPath("$prefix.slug").description("짧은 주소. /s/{slug}"),
            fieldWithPath("$prefix.name").description("이름"),
        )

    fun spaceFields(prefix: String): Array<FieldDescriptor> =
        spaceRefFields(prefix) +
            arrayOf(
                fieldWithPath("$prefix.description").description("설명, 500자 이하"),
                fieldWithPath("$prefix.links").description("외부 연결. 권한과 무관하며 참조 링크와 스페이스 찾기에만 쓴다"),
                fieldWithPath(
                    "$prefix.links[].kind",
                ).description("github_repository, github_organization, local_path, url").optional().type(JsonFieldType.STRING),
                fieldWithPath("$prefix.links[].value").description("정규화한 값. 레포는 owner/repo").optional().type(JsonFieldType.STRING),
                fieldWithPath("$prefix.formerSlugs").description("예전 주소. 계속 이 스페이스로 연결된다"),
                fieldWithPath("$prefix.createdAt").description("생성 시각"),
                fieldWithPath("$prefix.archivedAt")
                    .type(JsonFieldType.STRING)
                    .optional()
                    .description("보관한 시각. 보관한 스페이스는 목록·검색·에이전트의 스페이스 찾기에서 빠지고 주소로는 열린다"),
            )

    fun headerFields(prefix: String): Array<FieldDescriptor> =
        arrayOf(
            fieldWithPath("${prefix}path").description("스페이스 안 경로. 앞부분이 폴더다"),
            fieldWithPath("${prefix}title").description("제목"),
            fieldWithPath("${prefix}status").description("draft, in_review, confirmed, archived"),
            fieldWithPath("${prefix}tags").description("태그"),
            fieldWithPath("${prefix}assignee").type(JsonFieldType.STRING).optional().description("담당자 GitHub 로그인"),
            fieldWithPath("${prefix}revision").description("문서별 리비전 번호. 1부터 증가한다"),
            fieldWithPath("${prefix}createdAt").description("생성 시각"),
            fieldWithPath("${prefix}updatedAt").description("마지막 변경 시각"),
            fieldWithPath("${prefix}trashedAt").type(JsonFieldType.STRING).optional().description("휴지통에 넣은 시각. 휴지통 밖이면 null"),
        )

    fun blocksSubsection(path: String): FieldDescriptor =
        subsectionWithPath(path).description("블록 목록. 블록마다 id, type, hash와 유형별 필드가 있다. 형식은 '블록' 절을 본다")
}
