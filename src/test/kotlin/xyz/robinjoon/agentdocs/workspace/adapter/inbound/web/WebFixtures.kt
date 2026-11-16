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
            mapOf(UUID.fromString("33333333-3333-4333-8333-333333333333") to CallerScope.EDIT),
            CallerScope.EDIT,
        )
    val agent = CallerAuthentication(agentCaller)
    val ownerAccess = Access(Actor.human(userId, "robinjoon"), SpaceScope.AllSpaces, Permission.OWNER)
    val agentActor = Actor.agent(userId, "robinjoon", AgentIdentity(connectionId, "Claude Code"))
    val agentAccess =
        Access(
            agentActor,
            SpaceScope.Listed(mapOf(SpaceId(UUID.fromString("33333333-3333-4333-8333-333333333333")) to Permission.EDIT)),
            Permission.EDIT,
        )
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

    val inputTraceJson =
        """
        {
          "batchId":"22222222-2222-4222-8222-222222222222",
          "traces":[
            {
              "tabId":"11111111-1111-4111-8111-111111111111",
              "batchId":"33333333-3333-4333-8333-333333333333",
              "intervals":[{"firstInputAt":"2026-10-18T08:59:00Z","lastInputAt":"2026-10-18T08:59:10Z"}]
            },
            {
              "tabId":"44444444-4444-4444-8444-444444444444",
              "batchId":"55555555-5555-4555-8555-555555555555",
              "intervals":[{"firstInputAt":"2026-10-18T09:02:00Z","lastInputAt":"2026-10-18T09:02:10Z"}]
            }
          ]
        }
        """.trimIndent()

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
            fieldWithPath("${prefix}revision").description("마지막 확정 리비전 번호. 1부터 증가한다"),
            fieldWithPath("${prefix}version").description("현재 내용 버전. 실제 변경마다 증가한다"),
            fieldWithPath("${prefix}createdAt").description("생성 시각"),
            fieldWithPath("${prefix}updatedAt").description("마지막 변경 시각"),
            fieldWithPath("${prefix}trashedAt").type(JsonFieldType.STRING).optional().description("휴지통에 넣은 시각. 휴지통 밖이면 null"),
        )

    fun guideFields(prefix: String): Array<FieldDescriptor> =
        arrayOf(
            fieldWithPath(prefix).type(JsonFieldType.OBJECT).optional().description("스페이스 에이전트 지침. 없거나 현재 읽는 문서 자신이면 null. 쓰기 전에 읽는다"),
            fieldWithPath("$prefix.documentId").type(JsonFieldType.STRING).optional().description("지침 문서 ID"),
            fieldWithPath("$prefix.title").type(JsonFieldType.STRING).optional().description("지침 제목"),
            fieldWithPath("$prefix.path").type(JsonFieldType.STRING).optional().description("지침 경로"),
        )

    fun revisionMetadataFields(prefix: String): Array<FieldDescriptor> =
        arrayOf(
            fieldWithPath("${prefix}codeReferences").type(JsonFieldType.ARRAY).description("관련 코드 참조"),
            fieldWithPath("${prefix}codeReferences[].repository").type(JsonFieldType.STRING).optional().description("정규화한 owner/repo"),
            fieldWithPath(
                "${prefix}codeReferences[].commit",
            ).type(JsonFieldType.STRING).optional().description("전체 40자리 SHA, PR만 있으면 null"),
            fieldWithPath(
                "${prefix}codeReferences[].pullRequest",
            ).type(JsonFieldType.NUMBER).optional().description("양의 PR 번호, commit만 있으면 null"),
            fieldWithPath(
                "${prefix}codeReferences[].commitUrl",
            ).type(JsonFieldType.STRING).optional().description("서버가 만든 GitHub commit URL"),
            fieldWithPath(
                "${prefix}codeReferences[].pullRequestUrl",
            ).type(JsonFieldType.STRING).optional().description("서버가 만든 GitHub PR URL"),
            fieldWithPath("${prefix}importProvenance").type(JsonFieldType.OBJECT).optional().description("가져오기 출처, 없으면 null"),
            fieldWithPath("${prefix}importProvenance.tool").type(JsonFieldType.STRING).optional().description("원본 도구, 최대 100자"),
            fieldWithPath(
                "${prefix}importProvenance.originalPath",
            ).type(JsonFieldType.STRING).optional().description("원래 상대 경로, 최대 1000자. 절대·상위 경로 불가"),
        )

    fun writeMetadataFields(): Array<FieldDescriptor> =
        arrayOf(
            fieldWithPath("inputTrace").type(JsonFieldType.OBJECT).optional().description("실제 사람 입력 구간. 검증·통계 실패는 문서 성공 응답에 영향을 주지 않는다"),
            fieldWithPath(
                "inputTrace.tabId",
            ).type(JsonFieldType.STRING).optional().description("legacy 단일 형식의 전경 편집 세그먼트 UUID. 새 형식은 traces에 보낸다"),
            fieldWithPath(
                "inputTrace.batchId",
            ).type(JsonFieldType.STRING).optional().description("전체 성공 version의 안정 배치 UUID. legacy 단일 형식에서는 child batchId도 겸한다"),
            fieldWithPath(
                "inputTrace.intervals",
            ).type(JsonFieldType.ARRAY).optional().description("legacy 단일 형식의 필수 구간 목록. 새 형식은 traces[].intervals에 보낸다"),
            fieldWithPath(
                "inputTrace.intervals[].firstInputAt",
            ).type(JsonFieldType.STRING).optional().description("구간 첫 실제 입력 ISO-8601 시각"),
            fieldWithPath("inputTrace.intervals[].lastInputAt").type(JsonFieldType.STRING).optional().description("마지막 실제 입력 ISO-8601 시각"),
            fieldWithPath("inputTrace.traces")
                .type(JsonFieldType.ARRAY)
                .optional()
                .description("새 형식의 foreground 입력 trace 목록. 한 version과 전체 batchId로 원자 채택, 빈 목록 불가"),
            fieldWithPath("inputTrace.traces[].tabId")
                .type(JsonFieldType.STRING)
                .optional()
                .description("child 필수 전경 편집 세그먼트 UUID"),
            fieldWithPath("inputTrace.traces[].batchId")
                .type(JsonFieldType.STRING)
                .optional()
                .description("child 필수 snapshot 안정 UUID"),
            fieldWithPath("inputTrace.traces[].intervals")
                .type(JsonFieldType.ARRAY)
                .optional()
                .description("child 필수 실제 입력 구간 목록. 120초 이상 공백은 별도 구간"),
            fieldWithPath("inputTrace.traces[].intervals[].firstInputAt")
                .type(JsonFieldType.STRING)
                .optional()
                .description("구간 첫 실제 입력 ISO-8601 시각"),
            fieldWithPath("inputTrace.traces[].intervals[].lastInputAt")
                .type(JsonFieldType.STRING)
                .optional()
                .description("구간 마지막 실제 입력 ISO-8601 시각"),
            fieldWithPath("inputTrace.traces[].firstInputAt")
                .type(JsonFieldType.STRING)
                .optional()
                .description("호환 파생값. 서버는 intervals를 사용한다"),
            fieldWithPath("inputTrace.traces[].lastInputAt")
                .type(JsonFieldType.STRING)
                .optional()
                .description("호환 파생값. 서버는 intervals를 사용한다"),
            fieldWithPath("inputTrace.firstInputAt")
                .type(JsonFieldType.STRING)
                .optional()
                .description("legacy 호환 파생값. intervals를 대신하지 않는다"),
            fieldWithPath("inputTrace.lastInputAt")
                .type(JsonFieldType.STRING)
                .optional()
                .description("legacy 호환 파생값. intervals를 대신하지 않는다"),
            fieldWithPath(
                "codeReferences",
            ).type(JsonFieldType.ARRAY).optional().description("관련 코드 참조, 최대 20개. 각 참조에 commit 또는 pullRequest 필요"),
            fieldWithPath(
                "codeReferences[].repository",
            ).type(JsonFieldType.STRING).optional().description("owner/repo. 연결 레포가 정확히 하나일 때만 생략 가능"),
            fieldWithPath("codeReferences[].commit").type(JsonFieldType.STRING).optional().description("전체 40자리 commit SHA"),
            fieldWithPath("codeReferences[].pullRequest").type(JsonFieldType.NUMBER).optional().description("양의 PR 번호. 임의 URL은 받지 않는다"),
        )

    fun blocksSubsection(path: String): FieldDescriptor =
        subsectionWithPath(path).description("블록 목록. 블록마다 id, type, hash와 유형별 필드가 있다. 형식은 '블록' 절을 본다")
}
