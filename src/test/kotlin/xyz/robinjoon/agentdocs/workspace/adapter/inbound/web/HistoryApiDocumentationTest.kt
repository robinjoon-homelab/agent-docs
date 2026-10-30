package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import xyz.robinjoon.agentdocs.support.web.RestDocsWebTest
import xyz.robinjoon.agentdocs.support.web.documented
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.actorFields
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.agentActor
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.at
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.blocksSubsection
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.body
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.document
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.documentId
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.owner
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.ownerAccess
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.title
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.webContext
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentWrite
import xyz.robinjoon.agentdocs.workspace.application.model.RevisionEntry
import xyz.robinjoon.agentdocs.workspace.application.model.RevisionFilter
import xyz.robinjoon.agentdocs.workspace.application.model.RevisionSummary
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentQueryUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase
import xyz.robinjoon.agentdocs.workspace.domain.ActorKind
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.ChangeSource
import xyz.robinjoon.agentdocs.workspace.domain.document.InlineText
import xyz.robinjoon.agentdocs.workspace.domain.document.Paragraph
import xyz.robinjoon.agentdocs.workspace.domain.document.history.ChangeOrigin
import xyz.robinjoon.agentdocs.workspace.domain.document.history.ChangePeriod
import xyz.robinjoon.agentdocs.workspace.domain.document.history.Revision
import xyz.robinjoon.agentdocs.workspace.domain.document.history.RevisionChange
import xyz.robinjoon.agentdocs.workspace.domain.document.history.RevisionContent
import xyz.robinjoon.agentdocs.workspace.domain.document.history.RevisionDiff
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId
import java.util.UUID

class HistoryApiDocumentationTest : RestDocsWebTest() {
    private val queries = mockk<DocumentQueryUseCase>()
    private val documents = mockk<DocumentUseCase>()
    private val mockMvc = restDocsMockMvc(HistoryController(queries, documents), WorkspaceExceptionHandler())
    private val change =
        RevisionChange.restore(
            ChangeOrigin(agentActor, ChangeSource.MCP),
            "블록 1개 수정",
            listOf(body.id),
            ChangePeriod(at, at),
        )

    @Test
    fun `revisions come newest first with the threads they answered`() {
        val thread = ThreadId(UUID.fromString("66666666-6666-4666-8666-666666666666"))
        every { queries.revisions(ownerAccess, documentId, RevisionFilter(ActorKind.AGENT, null, null)) } returns
            listOf(RevisionEntry(RevisionSummary(4, "재시도 설계", change), listOf(thread)))

        mockMvc
            .perform(get("/api/documents/{id}/revisions?actor=agent", documentId).principal(owner))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].source").value("mcp"))
            .andDo(
                documented(
                    "history-revisions",
                    pathParameters(parameterWithName("id").description("문서 ID")),
                    queryParameters(
                        parameterWithName("actor").optional().description("human 또는 agent. 빠지면 모두"),
                        parameterWithName("from").optional().description("이 시각 이후 리비전만 (ISO-8601)"),
                        parameterWithName("to").optional().description("이 시각 이전 리비전만 (ISO-8601)"),
                    ),
                    responseFields(
                        fieldWithPath("[].revision").description("리비전 번호"),
                        fieldWithPath("[].title").description("그 리비전의 제목"),
                        *actorFields("[].actor"),
                        fieldWithPath("[].source").description("web, mcp, rest, system(앱이 스스로 바꾼 것)"),
                        fieldWithPath("[].summary").description("변경 요약. 비워 보내면 '블록 1개 수정'처럼 채운다"),
                        fieldWithPath("[].changedBlockIds").description("바뀐 블록 ID"),
                        fieldWithPath("[].createdAt").description("리비전이 생긴 시각"),
                        fieldWithPath("[].updatedAt").description("사람이 이어 쓴 마지막 시각"),
                        fieldWithPath("[].threadIds").description("이 리비전이 답한 스레드"),
                    ),
                ),
            )
    }

    @Test
    fun `a revision returns its full content`() {
        val revision = Revision(documentId, 2, RevisionContent.of(document().header, listOf(title), false), change)
        every { queries.revision(ownerAccess, documentId, 2) } returns revision

        mockMvc
            .perform(get("/api/documents/{id}/revisions/{number}", documentId, 2).principal(owner))
            .andExpect(status().isOk)
            .andDo(
                documented(
                    "history-revision",
                    pathParameters(parameterWithName("id").description("문서 ID"), parameterWithName("number").description("리비전 번호")),
                    responseFields(
                        fieldWithPath("revision").description("리비전 번호"),
                        fieldWithPath("title").description("제목"),
                        fieldWithPath("path").description("경로"),
                        blocksSubsection("blocks"),
                        *actorFields("actor"),
                        fieldWithPath("createdAt").description("시각"),
                    ),
                ),
            )
    }

    @Test
    fun `a diff lists block changes and a unified diff whose hunks name blocks`() {
        val newer = Paragraph(BlockId("para01"), InlineText("실패하면 **60초** 뒤에 다시 시도한다."))
        every { queries.diff(ownerAccess, documentId, 2, null) } returns
            RevisionDiff.between(2, listOf(title, body), 4, listOf(title, newer))

        mockMvc
            .perform(get("/api/documents/{id}/diff?from=2", documentId).principal(owner))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.changes[0].change").value("modified"))
            .andDo(
                documented(
                    "history-diff",
                    queryParameters(
                        parameterWithName("from").description("이전 리비전"),
                        parameterWithName("to").optional().description("나중 리비전. 빠지면 현재"),
                    ),
                    responseFields(
                        fieldWithPath("from").description("이전 리비전"),
                        fieldWithPath("to").description("나중 리비전"),
                        fieldWithPath("changes[].blockId").description("블록 ID"),
                        fieldWithPath("changes[].change").description("added, modified, removed. 옮기기만 한 블록은 없다"),
                        subsectionWithPath("changes[].before").type(JsonFieldType.OBJECT).optional().description("이전 블록 JSON"),
                        subsectionWithPath("changes[].after").type(JsonFieldType.OBJECT).optional().description("나중 블록 JSON"),
                        fieldWithPath("changes[].changedCells").description("표에서 바뀐 셀의 rowId, columnId"),
                        fieldWithPath("unified").description("마크다운 unified diff. 헝크마다 b:블록ID를 붙인다"),
                    ),
                ),
            )
    }

    @Test
    fun `restore writes the old content as a new revision`() {
        every { documents.restore(webContext, documentId, 2, 4) } returns
            DocumentWrite(document(revision = 5), 5, listOf(body.id), amended = false, unchanged = false)

        mockMvc
            .perform(
                post(
                    "/api/documents/{id}/restore",
                    documentId,
                ).principal(owner).contentType(MediaType.APPLICATION_JSON).content("""{"revision":2,"baseRevision":4}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.revision").value(5))
            .andDo(
                documented(
                    "history-restore",
                    requestFields(
                        fieldWithPath("revision").description("되돌릴 리비전. 이력은 되감지 않고 새 리비전을 만든다"),
                        fieldWithPath(
                            "baseRevision",
                        ).optional().type(JsonFieldType.NUMBER).description("화면이 보여 준 현재 리비전. 그 뒤 다른 변경이 있으면 409"),
                        fieldWithPath("opId").optional().type(JsonFieldType.STRING).description("멱등 키"),
                    ),
                    responseFields(
                        fieldWithPath("revision").description("새 리비전"),
                        fieldWithPath("changedBlockIds").description("바뀐 블록 ID"),
                    ),
                ),
            )
    }
}
