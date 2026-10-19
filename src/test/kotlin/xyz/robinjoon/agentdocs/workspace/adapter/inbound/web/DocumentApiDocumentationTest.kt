package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.headers.HeaderDocumentation.headerWithName
import org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import xyz.robinjoon.agentdocs.support.web.RestDocsWebTest
import xyz.robinjoon.agentdocs.support.web.documented
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.actorFields
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.agent
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.agentAccess
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.agentActor
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.at
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.blocksSubsection
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.body
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.document
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.documentId
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.headerFields
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.owner
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.ownerAccess
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.space
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.spaceRefFields
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.title
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.webContext
import xyz.robinjoon.agentdocs.workspace.application.model.AssigneeChange
import xyz.robinjoon.agentdocs.workspace.application.model.BlockMark
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentDraft
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentFilter
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentListing
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentView
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentWrite
import xyz.robinjoon.agentdocs.workspace.application.model.HeaderChange
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownChunk
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownFormat
import xyz.robinjoon.agentdocs.workspace.application.model.MarkdownRequest
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentQueryUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.PermissionDeniedException
import xyz.robinjoon.agentdocs.workspace.application.port.input.StaleRevisionException
import xyz.robinjoon.agentdocs.workspace.domain.Permission
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.ChangeSource
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentProperties
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentStatus
import xyz.robinjoon.agentdocs.workspace.domain.document.InlineText
import xyz.robinjoon.agentdocs.workspace.domain.document.Paragraph
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.BlockConflict
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.EditConflictException
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.EditOperation
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId
import java.util.UUID

// The use cases are strict mocks; merge, conflict, and revision rules belong to the domain and service tests. This test
// checks block JSON in both directions, statuses, and error conversion.
class DocumentApiDocumentationTest : RestDocsWebTest() {
    private val documents = mockk<DocumentUseCase>()
    private val queries = mockk<DocumentQueryUseCase>()
    private val mockMvc = restDocsMockMvc(DocumentController(documents, queries), WorkspaceExceptionHandler())
    private val listing = DocumentListing(documentId, document().header, document().lifecycle, agentActor)
    private val revised = Paragraph(BlockId("para01"), InlineText("실패하면 **60초** 뒤에 다시 시도한다."))

    @Test
    fun `list returns documents of the space filtered by status, tag, and folder`() {
        every {
            queries.list(ownerAccess, SpaceSlug("agent-docs"), DocumentFilter(DocumentStatus.IN_REVIEW, "payments", "payments", false))
        } returns listOf(listing)

        mockMvc
            .perform(get("/api/spaces/{slug}/documents?status=in_review&tag=payments&folder=payments", "agent-docs").principal(owner))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].updatedBy.kind").value("agent"))
            .andDo(
                documented(
                    "documents-list",
                    pathParameters(parameterWithName("slug").description("스페이스 주소")),
                    queryParameters(
                        parameterWithName("status").optional().description("draft, in_review, confirmed, archived"),
                        parameterWithName("tag").optional().description("태그"),
                        parameterWithName("folder").optional().description("이 폴더 아래 문서만"),
                    ),
                    responseFields(*listingFields("[].")),
                ),
            )
    }

    @Test
    fun `create from markdown returns the first revision and its block hashes`() {
        val created = document(listOf(title), revision = 1)
        val draft =
            DocumentDraft(
                DocumentPath("payments/retry"),
                "재시도 설계",
                DocumentProperties.of(listOf("payments"), DocumentStatus.DRAFT, null),
                listOf(title),
            )
        every { documents.create(webContext.copy(operationId = "op-1"), SpaceSlug("agent-docs"), draft) } returns
            DocumentWrite(created, 1, listOf(title.id), amended = false, unchanged = false)
        every { queries.open(ownerAccess, documentId) } returns DocumentView(created, space, emptyList())

        mockMvc
            .perform(
                post("/api/spaces/{slug}/documents", "agent-docs")
                    .principal(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"path":"payments/retry","title":"재시도 설계","status":"draft","tags":["payments"],"markdown":"<!-- b:head01 -->\n# 재시도 설계","opId":"op-1"}""",
                    ),
            ).andExpect(status().isCreated)
            .andExpect(header().string("Location", "/api/documents/$documentId"))
            .andDo(
                documented(
                    "documents-create",
                    requestFields(
                        fieldWithPath("path").description("스페이스 안 경로"),
                        fieldWithPath("title").description("제목, 200자 이하"),
                        fieldWithPath("status").optional().description("처음 상태. 기본 draft"),
                        fieldWithPath("tags").optional().description("태그"),
                        fieldWithPath("assignee").optional().type(JsonFieldType.STRING).description("담당자 GitHub 로그인"),
                        fieldWithPath("markdown").optional().description("본문 마크다운. blocks 대신 쓴다. `<!-- b:ID -->` 주석이 있으면 그 블록 ID를 쓴다"),
                        fieldWithPath("blocks").optional().type(JsonFieldType.ARRAY).description("본문 블록 JSON. 웹 에디터가 쓴다"),
                        fieldWithPath("opId").optional().description("멱등 키. 같은 키로 다시 보내면 처음 결과를 돌려준다"),
                        fieldWithPath("summary").optional().type(JsonFieldType.STRING).description("한 줄 변경 요약. 비우면 자동으로 채운다"),
                    ),
                    responseHeaders(headerWithName("Location").description("문서 조회 경로")),
                    responseFields(*documentFields()),
                ),
            )
    }

    @Test
    fun `markdown reads the same annotated text docs_read gives agents, in chunks`() {
        val request = MarkdownRequest(MarkdownFormat.PLAIN, null, null, 0)
        every { queries.markdown(ownerAccess, documentId, request) } returns
            MarkdownChunk(document(), "# 재시도 설계\n\n실패하면 **30초** 뒤에 다시 시도한다.\n", null, 2)

        mockMvc
            .perform(get("/api/documents/{id}/markdown?format=plain", documentId).principal(owner))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.markdown").value("# 재시도 설계\n\n실패하면 **30초** 뒤에 다시 시도한다.\n"))
            .andDo(
                documented(
                    "documents-markdown",
                    queryParameters(
                        parameterWithName("format").optional().description("markdown(블록 주석 포함, 기본), plain(주석 없음), outline(제목과 블록 크기)"),
                        parameterWithName("section").optional().description("이 제목 블록의 절만"),
                        parameterWithName("blocks").optional().description("이 블록들만. 여러 번 줄 수 있다"),
                        parameterWithName("cursor").optional().description("앞 조각의 nextCursor"),
                    ),
                    responseFields(
                        fieldWithPath("documentId").description("문서 ID"),
                        fieldWithPath("revision").description("읽은 리비전. 같은 리비전은 늘 같은 바이트다"),
                        fieldWithPath("markdown").description("마크다운"),
                        fieldWithPath("nextCursor").type(JsonFieldType.NUMBER).optional().description("남은 블록이 있으면 다음 cursor"),
                        fieldWithPath("totalBlocks").description("문서의 블록 수"),
                    ),
                ),
            )
    }

    @Test
    fun `open returns the blocks with hashes and the last change of each block`() {
        val view = DocumentView(document(), space, listOf(BlockMark(body.id, agentActor, 3, at)))
        every { queries.open(ownerAccess, documentId) } returns view

        mockMvc
            .perform(get("/api/documents/{id}", documentId).principal(owner))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.blocks[1].hash").value(BlockMarkdown.hash(body)))
            .andExpect(jsonPath("$.blocks[1].text").value("실패하면 **30초** 뒤에 다시 시도한다."))
            .andDo(
                documented(
                    "documents-open",
                    pathParameters(parameterWithName("id").description("문서 ID")),
                    responseFields(*documentFields()),
                ),
            )
    }

    @Test
    fun `save sends the whole block list against the revision it was based on`() {
        every { documents.save(webContext, documentId, 3, listOf(title, revised)) } returns
            DocumentWrite(document(listOf(title, revised), revision = 3), 3, listOf(revised.id), amended = true, unchanged = false)

        mockMvc
            .perform(
                put("/api/documents/{id}/content", documentId)
                    .principal(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"baseRevision":3,"blocks":[{"id":"head01","type":"heading","level":1,"text":"재시도 설계"},""" +
                            """{"id":"para01","type":"paragraph","text":" 실패하면 **60초** 뒤에 다시 시도한다. "}]}""",
                    ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.amended").value(true))
            .andDo(
                documented(
                    "documents-save",
                    pathParameters(parameterWithName("id").description("문서 ID")),
                    requestFields(
                        fieldWithPath("baseRevision").description("마지막으로 읽거나 저장한 리비전. 그 뒤 다른 블록의 변경은 합치고, 같은 블록이면 409"),
                        blocksSubsection("blocks"),
                        fieldWithPath("opId").optional().type(JsonFieldType.STRING).description("멱등 키"),
                    ),
                    responseFields(*writeFields()),
                ),
            )
    }

    @Test
    fun `a block both sides changed answers 409 with the block as it is now`() {
        every { documents.save(webContext, documentId, 2, listOf(revised)) } throws
            EditConflictException(listOf(BlockConflict(body.id, BlockMarkdown.hash(body), BlockMarkdown.of(body))))

        mockMvc
            .perform(
                put("/api/documents/{id}/content", documentId)
                    .principal(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"baseRevision":2,"blocks":[{"id":"para01","type":"paragraph","text":"실패하면 **60초** 뒤에 다시 시도한다."}]}"""),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.conflicts[0].current.text").value("실패하면 **30초** 뒤에 다시 시도한다."))
            .andDo(
                documented(
                    "documents-conflict",
                    responseFields(
                        *workspaceProblemFields(),
                        fieldWithPath("conflicts[].blockId").description("충돌한 블록 ID"),
                        fieldWithPath("conflicts[].currentHash").type(JsonFieldType.STRING).optional().description("현재 해시. 지워졌으면 null"),
                        fieldWithPath("conflicts[].currentMarkdown").type(JsonFieldType.STRING).optional().description("현재 블록의 마크다운"),
                        subsectionWithPath("conflicts[].current").type(JsonFieldType.OBJECT).optional().description("현재 블록 JSON"),
                    ),
                ),
            )
    }

    @Test
    fun `edits apply guarded operations and can answer a thread`() {
        val thread = ThreadId(UUID.fromString("66666666-6666-4666-8666-666666666666"))
        val context = WriteContext(agentAccess, ChangeSource.REST, "op-2", "재시도 간격 수정")
        val operations = listOf(EditOperation.ReplaceText(body.id, "30초", "60초"))
        every { documents.edit(context, documentId, operations, thread) } returns
            DocumentWrite(document(listOf(title, revised), revision = 4), 4, listOf(body.id), amended = false, unchanged = false)

        mockMvc
            .perform(
                post("/api/documents/{id}/edits", documentId)
                    .principal(agent)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"operations":[{"op":"replaceText","blockId":"para01","find":"30초","replace":"60초"}],""" +
                            """"answering":"$thread","opId":"op-2","summary":"재시도 간격 수정"}""",
                    ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.revision").value(4))
            .andDo(
                documented(
                    "documents-edit",
                    requestFields(
                        subsectionWithPath("operations").description("연산 목록. replace, insert, delete, move, replaceText. '편집 연산' 절을 본다"),
                        fieldWithPath("answering").optional().description("이 수정이 답하는 스레드. 새 리비전을 스레드에 연결한다"),
                        fieldWithPath("opId").optional().description("멱등 키"),
                        fieldWithPath("summary").optional().description("한 줄 변경 요약"),
                    ),
                    responseFields(*writeFields()),
                ),
            )
    }

    @Test
    fun `a header change keeps fields that are left out and clears an explicit null`() {
        val change = HeaderChange("재시도 정책", DocumentPath("payments/retry-policy"), DocumentStatus.CONFIRMED, null, AssigneeChange(null), 3)
        every { documents.changeHeader(webContext, documentId, change) } returns
            DocumentWrite(document(revision = 4), 4, emptyList(), amended = false, unchanged = false)
        every { queries.open(ownerAccess, documentId) } returns DocumentView(document(revision = 4), space, emptyList())

        mockMvc
            .perform(
                patch("/api/documents/{id}", documentId)
                    .principal(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"baseRevision":3,"title":"재시도 정책","path":"payments/retry-policy","status":"confirmed","assignee":null}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.revision").value(4))
            .andDo(
                documented(
                    "documents-change-header",
                    requestFields(
                        fieldWithPath("baseRevision").optional().description("마지막으로 본 리비전. 그 뒤 문서가 바뀌었으면 409"),
                        fieldWithPath("title").optional().description("새 제목"),
                        fieldWithPath("path").optional().description("새 경로. 같은 스페이스 안에서 옮긴다"),
                        fieldWithPath("status").optional().description("새 상태"),
                        fieldWithPath("tags").optional().type(JsonFieldType.ARRAY).description("태그 전체를 이 목록으로 바꾼다"),
                        fieldWithPath("assignee").optional().type(JsonFieldType.NULL).description("담당자. null이면 지우고, 빠지면 그대로 둔다"),
                        fieldWithPath("opId").optional().type(JsonFieldType.STRING).description("멱등 키"),
                    ),
                    responseFields(*documentFields()),
                ),
            )
    }

    @Test
    fun `a header change based on an older revision answers 409`() {
        every { documents.changeHeader(webContext, documentId, HeaderChange("x", null, null, null, null, 2)) } throws
            StaleRevisionException(2, 3)

        mockMvc
            .perform(
                patch(
                    "/api/documents/{id}",
                    documentId,
                ).principal(owner).contentType(MediaType.APPLICATION_JSON).content("""{"baseRevision":2,"title":"x"}"""),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("conflict"))
            .andDo(documented("documents-stale-revision", responseFields(*workspaceProblemFields())))
    }

    @Test
    fun `trash, untrash, and permanent delete answer 204`() {
        every { documents.trash(webContext, documentId) } returns
            DocumentWrite(document(), 4, emptyList(), amended = false, unchanged = false)
        every { documents.untrash(webContext, documentId) } returns
            DocumentWrite(document(), 5, emptyList(), amended = false, unchanged = false)
        every { documents.purge(webContext, documentId) } just runs

        mockMvc
            .perform(
                post("/api/documents/{id}/trash", documentId).principal(owner),
            ).andExpect(status().isNoContent)
            .andDo(documented("documents-trash"))
        mockMvc
            .perform(
                post("/api/documents/{id}/untrash", documentId).principal(owner),
            ).andExpect(status().isNoContent)
            .andDo(documented("documents-untrash"))
        mockMvc
            .perform(
                delete("/api/documents/{id}", documentId).principal(owner),
            ).andExpect(status().isNoContent)
            .andDo(documented("documents-purge"))
    }

    @Test
    fun `trash and recent lists use the list shape`() {
        every { queries.list(ownerAccess, SpaceSlug("agent-docs"), DocumentFilter(null, null, null, true)) } returns listOf(listing)
        every { queries.recent(ownerAccess, 5) } returns listOf(listing)

        mockMvc
            .perform(get("/api/spaces/{slug}/trash", "agent-docs").principal(owner))
            .andExpect(status().isOk)
            .andDo(documented("documents-trash-list", responseFields(*listingFields("[]."))))
        mockMvc
            .perform(get("/api/documents/recent?limit=5").principal(owner))
            .andExpect(status().isOk)
            .andDo(
                documented(
                    "documents-recent",
                    queryParameters(parameterWithName("limit").optional().description("최대 개수, 기본 20")),
                    responseFields(*listingFields("[].")),
                ),
            )
    }

    @Test
    fun `an agent that may only read gets 403 and an unknown document 404`() {
        every { documents.trash(any(), documentId) } throws PermissionDeniedException(Permission.EDIT, "editing a document")
        every { queries.open(ownerAccess, documentId) } throws DocumentNotFoundException(documentId)

        mockMvc
            .perform(post("/api/documents/{id}/trash", documentId).principal(agent))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.fix").value("ask the owner to allow edit for this connection"))
            .andDo(documented("documents-forbidden", responseFields(*workspaceProblemFields())))
        mockMvc.perform(get("/api/documents/{id}", documentId).principal(owner)).andExpect(status().isNotFound)
    }

    private fun listingFields(prefix: String): Array<FieldDescriptor> =
        arrayOf(fieldWithPath("${prefix}id").description("문서 ID")) + headerFields(prefix) + actorFields("${prefix}updatedBy")

    private fun documentFields(): Array<FieldDescriptor> =
        arrayOf(
            fieldWithPath("id").description("문서 ID. 이름을 바꾸거나 옮겨도 그대로다"),
            *spaceRefFields("space"),
            *headerFields(""),
            blocksSubsection("blocks"),
            fieldWithPath("blockChanges").description("블록마다 마지막 변경. 에이전트가 고친 블록 표시에 쓴다"),
            fieldWithPath("blockChanges[].blockId").optional().type(JsonFieldType.STRING).description("블록 ID"),
            fieldWithPath("blockChanges[].revision").optional().type(JsonFieldType.NUMBER).description("그 변경의 리비전"),
            fieldWithPath("blockChanges[].at").optional().type(JsonFieldType.STRING).description("변경 시각"),
            *actorFields("blockChanges[].actor").map { it.optional() }.toTypedArray(),
        )

    private fun writeFields(): Array<FieldDescriptor> =
        arrayOf(
            fieldWithPath("id").description("문서 ID"),
            fieldWithPath("revision").description("이 쓰기가 만든 리비전. amended면 이어 쓴 리비전"),
            fieldWithPath("amended").description("사람이 30초 안에 이어 쓴 저장이라 새 리비전 대신 마지막 리비전을 고쳤는지"),
            fieldWithPath("unchanged").description("바뀐 것이 없어 리비전을 만들지 않았는지"),
            fieldWithPath("changedBlockIds").description("바뀐 블록 ID"),
            fieldWithPath("merged").description("오래된 baseRevision에 다른 쪽의 변경을 합쳤는지. true면 받은 blocks로 화면을 맞춘다"),
            blocksSubsection("blocks"),
        )
}
