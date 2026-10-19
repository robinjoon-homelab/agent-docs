package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import xyz.robinjoon.agentdocs.support.web.RestDocsWebTest
import xyz.robinjoon.agentdocs.support.web.documented
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.actorFields
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.agent
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.agentAccess
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.agentActor
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.at
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.body
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.documentId
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.owner
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.ownerAccess
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.spaceId
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.webContext
import xyz.robinjoon.agentdocs.workspace.application.model.AnchorRequest
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadChange
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadContext
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadQuery
import xyz.robinjoon.agentdocs.workspace.application.model.ThreadView
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.input.ThreadUseCase
import xyz.robinjoon.agentdocs.workspace.domain.ActorKind
import xyz.robinjoon.agentdocs.workspace.domain.document.ChangeSource
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import xyz.robinjoon.agentdocs.workspace.domain.thread.Anchor
import xyz.robinjoon.agentdocs.workspace.domain.thread.AnchorResolution
import xyz.robinjoon.agentdocs.workspace.domain.thread.Comment
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentId
import xyz.robinjoon.agentdocs.workspace.domain.thread.CommentThread
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadActionException
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadLocation
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadStatus
import java.util.UUID

class ThreadApiDocumentationTest : RestDocsWebTest() {
    private val threads = mockk<ThreadUseCase>()
    private val mockMvc = restDocsMockMvc(ThreadController(threads), WorkspaceExceptionHandler())
    private val threadId = ThreadId(UUID.fromString("66666666-6666-4666-8666-666666666666"))
    private val commentId = CommentId(UUID.fromString("77777777-7777-4777-8777-777777777777"))
    private val anchor = Anchor.Text(body.id, 5, 8, "30초")
    private val thread =
        CommentThread.open(
            threadId,
            ThreadLocation(spaceId, documentId, anchor, 3),
            Comment.write(commentId, ownerAccess.actor, "60초로 늘려 줘", at),
            at,
        )
    private val view = ThreadView(thread, AnchorResolution.Attached(5, 8), null)

    @Test
    fun `the threads of a document carry where their anchor sits now`() {
        every { threads.forDocument(ownerAccess, documentId) } returns listOf(view)

        mockMvc
            .perform(get("/api/documents/{id}/threads", documentId).principal(owner))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].resolution.state").value("attached"))
            .andDo(documented("threads-for-document", responseFields(*threadFields("[]."))))
    }

    @Test
    fun `opening a thread on a text range takes the quote and its offset`() {
        every { threads.open(webContext, documentId, AnchorRequest.Text(body.id, "30초", 5), "60초로 늘려 줘") } returns view

        mockMvc
            .perform(
                post("/api/documents/{id}/threads", documentId)
                    .principal(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"anchor":{"kind":"text","blockId":"para01","quote":"30초","start":5},"body":"60초로 늘려 줘"}"""),
            ).andExpect(status().isCreated)
            .andDo(
                documented(
                    "threads-open",
                    requestFields(
                        fieldWithPath("anchor.kind").description("text, cell, block, document"),
                        fieldWithPath("anchor.blockId").optional().description("블록 ID. document에는 없다"),
                        fieldWithPath("anchor.quote").optional().description("text: 블록 plain text에서 고른 글자"),
                        fieldWithPath("anchor.start").optional().description("text: quote의 시작 위치. quote가 한 번만 나오면 생략할 수 있다"),
                        fieldWithPath("anchor.rowId").optional().type(JsonFieldType.STRING).description("cell: 행 ID"),
                        fieldWithPath("anchor.columnId").optional().type(JsonFieldType.STRING).description("cell: 열 ID"),
                        fieldWithPath("body").description("댓글 마크다운. @login은 그 사람의 받은편지함으로 간다"),
                        fieldWithPath("opId").optional().type(JsonFieldType.STRING).description("멱등 키"),
                    ),
                    responseFields(*threadFields("")),
                ),
            )
    }

    @Test
    fun `an agent reply can mark the thread answered`() {
        val context = WriteContext(agentAccess, ChangeSource.REST, null, null)
        val reply = Comment.write(CommentId(UUID.randomUUID()), agentActor, "60초로 바꿨어요", at)
        val answered = requireNotNull(thread.reply(reply, at).markAnswered(at))
        every { threads.reply(context, threadId, "60초로 바꿨어요", true) } returns
            ThreadView(answered, AnchorResolution.Attached(5, 8), null, written = reply.id)

        mockMvc
            .perform(
                post("/api/threads/{id}/comments", threadId.value)
                    .principal(agent)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"body":"60초로 바꿨어요","markAnswered":true}"""),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.author.kind").value("agent"))
            .andDo(
                documented(
                    "threads-reply",
                    requestFields(
                        fieldWithPath("body").description("답글 마크다운"),
                        fieldWithPath("markAnswered").optional().description("요청을 처리했으면 true. 열린 스레드를 '답변함'으로 바꾼다"),
                        fieldWithPath("opId").optional().type(JsonFieldType.STRING).description("멱등 키"),
                    ),
                    responseFields(*commentFields("")),
                ),
            )
    }

    @Test
    fun `an agent cannot resolve a thread a person opened`() {
        every {
            threads.change(WriteContext(agentAccess, ChangeSource.REST, null, null), threadId, ThreadChange(status = ThreadStatus.RESOLVED))
        } throws
            ThreadActionException(
                "an agent cannot resolve a thread a person opened",
                "mark the thread answered; the person who opened it resolves it",
            )

        mockMvc
            .perform(
                patch(
                    "/api/threads/{id}",
                    threadId.value,
                ).principal(agent).contentType(MediaType.APPLICATION_JSON).content("""{"status":"resolved"}"""),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.fix").value("mark the thread answered; the person who opened it resolves it"))
            .andDo(documented("threads-resolve-forbidden", responseFields(*workspaceProblemFields())))
    }

    @Test
    fun `a person resolves a thread`() {
        val resolved = requireNotNull(thread.resolve(ownerAccess.actor, at))
        every { threads.change(webContext, threadId, ThreadChange(status = ThreadStatus.RESOLVED)) } returns
            ThreadView(resolved, AnchorResolution.Attached(5, 8), null)

        mockMvc
            .perform(
                patch(
                    "/api/threads/{id}",
                    threadId.value,
                ).principal(owner).contentType(MediaType.APPLICATION_JSON).content("""{"status":"resolved"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("resolved"))
            .andDo(
                documented(
                    "threads-change",
                    requestFields(
                        fieldWithPath("status").optional().description("open, answered, resolved. 사람이 연 스레드는 에이전트가 resolved로 바꿀 수 없다"),
                        subsectionWithPath("anchor").optional().type(JsonFieldType.OBJECT).description("다른 위치에 다시 달기. 형식은 스레드 열기와 같다"),
                        fieldWithPath("linkRevision").optional().type(JsonFieldType.NUMBER).description("이 스레드에 연결할 리비전"),
                    ),
                    responseFields(*threadFields("")),
                ),
            )
    }

    @Test
    fun `a comment is edited and deleted by its author only`() {
        val edited = thread.editComment(commentId, ownerAccess.actor, "90초로 늘려 줘", at)
        every { threads.editComment(webContext, commentId, "90초로 늘려 줘") } returns ThreadView(edited, AnchorResolution.Attached(5, 8), null)
        every { threads.deleteComment(webContext, commentId) } returns
            ThreadView(thread.deleteComment(commentId, ownerAccess.actor, at), AnchorResolution.Attached(5, 8), null)

        mockMvc
            .perform(
                patch(
                    "/api/comments/{id}",
                    commentId.value,
                ).principal(owner).contentType(MediaType.APPLICATION_JSON).content("""{"body":"90초로 늘려 줘"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.editedAt").exists())
            .andDo(
                documented(
                    "comments-edit",
                    requestFields(fieldWithPath("body").description("새 본문. 이전 본문은 수정 이력으로 남는다")),
                    responseFields(*commentFields("")),
                ),
            )
        mockMvc
            .perform(
                delete("/api/comments/{id}", commentId.value).principal(owner),
            ).andExpect(status().isNoContent)
            .andDo(documented("comments-delete"))
    }

    @Test
    fun `the thread query is how an agent finds work, with the anchored block and its neighbors`() {
        val context =
            ThreadContext(
                SpaceSlug("agent-docs"),
                DocumentPath("payments/retry"),
                "재시도 설계",
                "<!-- b:para01 h:1a2b3c4d -->\n실패하면…",
                null,
                null,
            )
        val query = ThreadQuery(setOf(spaceId), null, setOf(ThreadStatus.OPEN), ActorKind.HUMAN, "robinjoon", at.minusSeconds(3_600))
        every { threads.query(agentAccess, query) } returns listOf(view.copy(context = context))

        mockMvc
            .perform(
                get(
                    "/api/threads?space={space}&status=open&authorKind=human&mention=robinjoon&since={since}",
                    spaceId.value,
                    at.minusSeconds(3_600),
                ).principal(agent),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$[0].context.anchorBlock").exists())
            .andDo(
                documented(
                    "threads-query",
                    queryParameters(
                        parameterWithName("space").optional().description("스페이스 ID. 빠지면 볼 수 있는 모든 스페이스"),
                        parameterWithName("document").optional().description("문서 ID"),
                        parameterWithName("status").optional().description("open, answered, resolved. 여러 번 줄 수 있다"),
                        parameterWithName("authorKind").optional().description("스레드를 연 쪽. human 또는 agent"),
                        parameterWithName("mention").optional().description("이 로그인을 멘션한 스레드만"),
                        parameterWithName("since").optional().description("이 시각 뒤에 바뀐 스레드만"),
                    ),
                    responseFields(
                        *threadFields("[]."),
                        fieldWithPath("[].context.space").description("스페이스 주소"),
                        fieldWithPath("[].context.path").description("문서 경로"),
                        fieldWithPath("[].context.title").description("문서 제목"),
                        fieldWithPath("[].context.anchorBlock").type(JsonFieldType.STRING).optional().description("앵커 블록의 현재 주석 마크다운"),
                        fieldWithPath("[].context.previousBlock").type(JsonFieldType.STRING).optional().description("앞 블록"),
                        fieldWithPath("[].context.nextBlock").type(JsonFieldType.STRING).optional().description("뒤 블록"),
                    ),
                ),
            )
    }

    private fun threadFields(prefix: String): Array<FieldDescriptor> =
        arrayOf(
            fieldWithPath("${prefix}id").description("스레드 ID"),
            fieldWithPath("${prefix}documentId").description("문서 ID"),
            subsectionWithPath("${prefix}anchor").description("앵커. kind와 종류별 필드(blockId, start, end, quote, rowId, columnId)"),
            fieldWithPath("${prefix}anchoredRevision").description("앵커를 단 리비전"),
            fieldWithPath("${prefix}resolution.state").description("attached 또는 detached. 대상이 사라지면 detached"),
            fieldWithPath("${prefix}resolution.start").type(JsonFieldType.NUMBER).optional().description("지금 문서에서의 시작 위치"),
            fieldWithPath("${prefix}resolution.end").type(JsonFieldType.NUMBER).optional().description("지금 문서에서의 끝 위치"),
            fieldWithPath("${prefix}status").description("open, answered, resolved"),
            *actorFields("${prefix}author"),
            fieldWithPath("${prefix}comments").description("댓글. 첫 댓글이 스레드를 연 댓글"),
            *commentFields("${prefix}comments[].").map { it.optional() }.toTypedArray(),
            fieldWithPath("${prefix}linkedRevisions").description("이 스레드에 답한 리비전"),
            fieldWithPath("${prefix}createdAt").description("연 시각"),
            fieldWithPath("${prefix}updatedAt").description("마지막 변화 시각"),
            fieldWithPath("${prefix}resolvedAt").type(JsonFieldType.STRING).optional().description("해결한 시각"),
        )

    private fun commentFields(prefix: String): Array<FieldDescriptor> =
        arrayOf(
            fieldWithPath("${prefix}id").type(JsonFieldType.STRING).description("댓글 ID"),
            *actorFields("${prefix}author"),
            fieldWithPath("${prefix}body").type(JsonFieldType.STRING).description("본문 마크다운. 삭제된 댓글은 빈 문자열"),
            fieldWithPath("${prefix}createdAt").type(JsonFieldType.STRING).description("쓴 시각"),
            fieldWithPath("${prefix}editedAt").type(JsonFieldType.STRING).optional().description("마지막으로 고친 시각"),
            fieldWithPath("${prefix}deleted").type(JsonFieldType.BOOLEAN).description("'삭제된 댓글'로 남았는지"),
        )
}
