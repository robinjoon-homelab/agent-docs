package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import xyz.robinjoon.agentdocs.support.web.RestDocsWebTest
import xyz.robinjoon.agentdocs.support.web.documented
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.actorFields
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.agentActor
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.at
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.body
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.documentId
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.owner
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.ownerAccess
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.space
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.spaceId
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.spaceRefFields
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentRef
import xyz.robinjoon.agentdocs.workspace.application.model.InboxView
import xyz.robinjoon.agentdocs.workspace.application.model.SearchHit
import xyz.robinjoon.agentdocs.workspace.application.model.SearchHitKind
import xyz.robinjoon.agentdocs.workspace.application.port.input.InboxUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.SearchUseCase
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import xyz.robinjoon.agentdocs.workspace.domain.inbox.InboxItem
import xyz.robinjoon.agentdocs.workspace.domain.inbox.InboxItemId
import xyz.robinjoon.agentdocs.workspace.domain.inbox.InboxKind
import xyz.robinjoon.agentdocs.workspace.domain.inbox.Notice
import xyz.robinjoon.agentdocs.workspace.domain.inbox.NoticeTarget
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId
import java.util.UUID

class SearchInboxApiDocumentationTest : RestDocsWebTest() {
    private val search = mockk<SearchUseCase>()
    private val inbox = mockk<InboxUseCase>()
    private val searchMvc = restDocsMockMvc(SearchController(search), WorkspaceExceptionHandler())
    private val inboxMvc = restDocsMockMvc(InboxController(inbox), WorkspaceExceptionHandler())
    private val ref = DocumentRef(documentId, DocumentPath("payments/retry"), "재시도 설계")
    private val itemId = InboxItemId(UUID.fromString("99999999-9999-4999-8999-999999999999"))

    @Test
    fun `search finds titles, blocks, and comments with a snippet`() {
        every { search.search(ownerAccess, "재시도", SpaceSlug("agent-docs")) } returns
            listOf(SearchHit(SearchHitKind.BLOCK, space, ref, body.id, null, "실패하면 30초 뒤에 다시 시도한다."))

        searchMvc
            .perform(get("/api/search?q=재시도&space=agent-docs").principal(owner))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].kind").value("block"))
            .andDo(
                documented(
                    "search",
                    queryParameters(
                        parameterWithName("q").description("찾을 글자, 1~100자. 부분 일치"),
                        parameterWithName("space").optional().description("스페이스 주소. 빠지면 볼 수 있는 모든 스페이스"),
                    ),
                    responseFields(
                        fieldWithPath("[].kind").description("title, block, comment"),
                        *spaceRefFields("[].space"),
                        fieldWithPath("[].documentId").description("문서 ID"),
                        fieldWithPath("[].path").description("문서 경로"),
                        fieldWithPath("[].title").description("문서 제목"),
                        fieldWithPath("[].blockId").type(JsonFieldType.STRING).optional().description("일치한 블록"),
                        fieldWithPath("[].threadId").type(JsonFieldType.STRING).optional().description("일치한 댓글의 스레드"),
                        fieldWithPath("[].snippet").description("일치한 부분 앞뒤 40자"),
                    ),
                ),
            )
    }

    @Test
    fun `title search jumps to documents across spaces`() {
        every { search.titles(ownerAccess, "재시") } returns listOf(space to ref)

        searchMvc
            .perform(get("/api/search/titles?q=재시").principal(owner))
            .andExpect(status().isOk)
            .andDo(
                documented(
                    "search-titles",
                    queryParameters(parameterWithName("q").description("제목에서 찾을 글자")),
                    responseFields(
                        *spaceRefFields("[].space"),
                        fieldWithPath("[].documentId").description("문서 ID"),
                        fieldWithPath("[].path").description("경로"),
                        fieldWithPath("[].title").description("제목"),
                    ),
                ),
            )
    }

    @Test
    fun `the inbox lists mentions, replies, and comments on assigned documents`() {
        val target = NoticeTarget(spaceId, documentId, ThreadId(UUID.randomUUID()), CommentId(UUID.randomUUID()))
        val item = InboxItem.deliver(itemId, "robinjoon", Notice.of(InboxKind.REPLY, target, agentActor, "60초로 바꿨어요"), at)
        every { inbox.list(ownerAccess, true) } returns listOf(InboxView(item, space, "재시도 설계"))
        every { inbox.unreadCount(ownerAccess) } returns 1

        inboxMvc
            .perform(get("/api/inbox?unread=true").principal(owner))
            .andExpect(status().isOk)
            .andDo(
                documented(
                    "inbox-list",
                    queryParameters(parameterWithName("unread").optional().description("true면 읽지 않은 항목만")),
                    responseFields(
                        fieldWithPath("[].id").description("항목 ID"),
                        fieldWithPath("[].kind").description("mention, reply, assigned_comment"),
                        *spaceRefFields("[].space"),
                        fieldWithPath("[].documentId").description("문서 ID"),
                        fieldWithPath("[].documentTitle").description("문서 제목"),
                        fieldWithPath("[].threadId").description("스레드 ID"),
                        fieldWithPath("[].commentId").description("댓글 ID"),
                        *actorFields("[].actor"),
                        fieldWithPath("[].excerpt").description("댓글 앞부분 200자"),
                        fieldWithPath("[].createdAt").description("받은 시각"),
                        fieldWithPath("[].readAt").type(JsonFieldType.STRING).optional().description("읽은 시각"),
                    ),
                ),
            )
        inboxMvc
            .perform(get("/api/inbox/count").principal(owner))
            .andExpect(jsonPath("$.unread").value(1))
            .andDo(documented("inbox-count", responseFields(fieldWithPath("unread").description("읽지 않은 항목 수"))))
    }

    @Test
    fun `items are marked read one by one or all at once`() {
        every { inbox.markRead(ownerAccess, itemId) } returns Unit
        every { inbox.markAllRead(ownerAccess) } returns 3

        inboxMvc
            .perform(
                post("/api/inbox/{id}/read", itemId.value).principal(owner),
            ).andExpect(status().isNoContent)
            .andDo(documented("inbox-read"))
        inboxMvc.perform(post("/api/inbox/read-all").principal(owner)).andExpect(status().isNoContent).andDo(documented("inbox-read-all"))
    }
}
