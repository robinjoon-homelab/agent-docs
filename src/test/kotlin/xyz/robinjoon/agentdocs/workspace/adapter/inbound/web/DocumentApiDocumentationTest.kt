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
import xyz.robinjoon.agentdocs.access.adapter.inbound.web.CallerAuthentication
import xyz.robinjoon.agentdocs.access.application.model.Caller
import xyz.robinjoon.agentdocs.access.application.model.CallerScope
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
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.WebFixtures.member
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
import xyz.robinjoon.agentdocs.workspace.application.model.OperationRecord
import xyz.robinjoon.agentdocs.workspace.application.model.OperationResultState
import xyz.robinjoon.agentdocs.workspace.application.model.SpaceReference
import xyz.robinjoon.agentdocs.workspace.application.model.WriteContext
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentNotFoundException
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentQueryUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.LegacyOperationException
import xyz.robinjoon.agentdocs.workspace.application.port.input.PermissionDeniedException
import xyz.robinjoon.agentdocs.workspace.application.port.input.ResultRemovedException
import xyz.robinjoon.agentdocs.workspace.application.port.input.StaleRevisionException
import xyz.robinjoon.agentdocs.workspace.domain.Permission
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.ChangeSource
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentHeader
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentLifecycle
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentProperties
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentStatus
import xyz.robinjoon.agentdocs.workspace.domain.document.InlineText
import xyz.robinjoon.agentdocs.workspace.domain.document.Paragraph
import xyz.robinjoon.agentdocs.workspace.domain.document.PathTakenException
import xyz.robinjoon.agentdocs.workspace.domain.document.edit.EditOperation
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown
import xyz.robinjoon.agentdocs.workspace.domain.space.Space
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceProfile
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceSlug
import xyz.robinjoon.agentdocs.workspace.domain.thread.ThreadId
import java.util.UUID

// The use cases are strict mocks; version, conflict, and revision rules belong to the domain and service tests. This test
// checks block JSON in both directions, statuses, and error conversion.
class DocumentApiDocumentationTest : RestDocsWebTest() {
    private val editSessions =
        mockk<xyz.robinjoon.agentdocs.workspace.application.port.input.EditSessionUseCase>(relaxed = true) {
            every { recordHuman(any(), any()) } returns emptyList()
        }
    private val documents = mockk<DocumentUseCase>()
    private val spaces =
        mockk<xyz.robinjoon.agentdocs.workspace.application.port.input.SpaceUseCase> {
            every { agentGuide(any(), any()) } returns null
        }
    private val queries = mockk<DocumentQueryUseCase>()
    private val mockMvc =
        restDocsMockMvc(
            DocumentController(documents, queries, spaces, EditStatisticsRecorder(editSessions)),
            WorkspaceExceptionHandler(),
        )
    private val listing = DocumentListing(documentId, document().header, document().lifecycle, agentActor)
    private val revised = Paragraph(BlockId("para01"), InlineText("실패하면 **60초** 뒤에 다시 시도한다."))

    @Test
    fun `invalid optional input trace preserves a successful document save`() {
        every { documents.save(webContext, documentId, 3, listOf(revised)) } returns
            DocumentWrite(document(listOf(revised), revision = 4), 4, listOf(revised.id), false, false)
        mockMvc
            .perform(
                put("/api/documents/{id}/content", documentId)
                    .principal(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"baseVersion":3,"blocks":[{"id":"para01","type":"paragraph","text":"실패하면 **60초** 뒤에 다시 시도한다."}],"inputTrace":{"tabId":"invalid"}}""",
                    ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.version").value(4))
            .andExpect(jsonPath("$.unchanged").value(false))
    }

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
                        parameterWithName("folder").optional().description("이 경로의 문서 아래에 있는 문서만"),
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
            DocumentWrite(
                created,
                1,
                listOf(title.id),
                amended = false,
                unchanged = false,
                marks = listOf(BlockMark(title.id, agentActor, 1, at)),
            )
        every { queries.open(ownerAccess, documentId) } returns DocumentView(document(revision = 9), space, emptyList())

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
            .andExpect(jsonPath("$.revision").value(1))
            .andExpect(jsonPath("$.blockChanges[0].revision").value(1))
            .andDo(
                documented(
                    "documents-create",
                    requestFields(
                        *WebFixtures.writeMetadataFields(),
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
    fun `REST guide metadata leaves markdown bytes unchanged`() {
        val markdown = "---\nversion: 3\n---\n\n본문\n"
        every { queries.markdown(ownerAccess, documentId, any()) } returns MarkdownChunk(document(), markdown, null, 2)
        every { spaces.agentGuide(ownerAccess, any()) } returns
            xyz.robinjoon.agentdocs.workspace.application.model.SpaceGuide.Offered(
                xyz.robinjoon.agentdocs.workspace.domain.document
                    .DocumentId(UUID.randomUUID()),
                "작성 지침",
                "guide",
            )
        mockMvc
            .perform(get("/api/documents/{id}/markdown", documentId).principal(owner))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.markdown").value(markdown))
            .andExpect(jsonPath("$.agentGuide.title").value("작성 지침"))
    }

    @Test
    fun `markdown reads the same annotated text docs_read gives agents, in chunks`() {
        val markdown =
            """
            <!-- b:list01 h:aaaabbbb -->
              - 들여쓴 항목

            <!-- b:table1 h:ccccdddd cols:col001,col002 -->
            | <!--r:row001-->항목 | 값 |
            | --- | --- |
            | <!--r:row002-->재시도 | 30초 |
            """.trimIndent() + "\n"
        val request = MarkdownRequest(MarkdownFormat.ANNOTATED, null, null, 0)
        every { queries.markdown(ownerAccess, documentId, request) } returns MarkdownChunk(document(), markdown, 2, 3)
        val plain = "# 재시도 설계\n\n실패하면 **30초** 뒤에 다시 시도한다.\n"
        every { queries.markdown(ownerAccess, documentId, MarkdownRequest(MarkdownFormat.PLAIN, null, null, 0)) } returns
            MarkdownChunk(document(), plain, null, 2)

        mockMvc
            .perform(get("/api/documents/{id}/markdown?format=plain", documentId).principal(owner))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.markdown").value(plain))
        mockMvc
            .perform(get("/api/documents/{id}/markdown?format=markdown&cursor=0", documentId).principal(owner))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.markdown").value(markdown))
            .andExpect(jsonPath("$.documentId").value(documentId.toString()))
            .andExpect(jsonPath("$.version").value(3))
            .andExpect(jsonPath("$.nextCursor").value(2))
            .andExpect(jsonPath("$.totalBlocks").value(3))
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
                        fieldWithPath("version").description("현재 내용 버전"),
                        fieldWithPath("revision").description("마지막 확정 리비전"),
                        fieldWithPath("markdown").description("마크다운"),
                        *WebFixtures.guideFields("agentGuide"),
                        fieldWithPath("nextCursor").type(JsonFieldType.NUMBER).optional().description("남은 블록이 있으면 다음 cursor"),
                        fieldWithPath("totalBlocks").description("문서의 블록 수"),
                    ),
                ),
            )
    }

    @Test
    fun `the internal annotated format name is not a supported HTTP format`() {
        mockMvc
            .perform(get("/api/documents/{id}/markdown?format=annotated&cursor=0", documentId).principal(owner))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("invalid"))
            .andExpect(jsonPath("$.detail").value("format must be markdown, plain, or outline"))
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
            .andExpect(jsonPath("$.permission").value("owner"))
            .andDo(
                documented(
                    "documents-open",
                    pathParameters(parameterWithName("id").description("문서 ID")),
                    responseFields(*documentFields()),
                ),
            )
    }

    @Test
    fun `a retried write answers with the permission of the space the document is in now, not of its first result`() {
        val moved =
            Space.create(
                SpaceId(UUID.fromString("44444444-4444-4444-8444-444444444444")),
                SpaceSlug("payments"),
                SpaceProfile.of("Payments", "", emptyList()),
                at,
            )
        val current =
            Document.restore(
                documentId,
                DocumentHeader(moved.id, DocumentPath("retry"), "재시도 설계", WebFixtures.properties),
                listOf(title, body),
                DocumentLifecycle(9, at.minusSeconds(3_600), at, null),
            )
        val grants = mapOf(space.id.value to CallerScope.COMMENT, moved.id.value to CallerScope.EDIT)
        val mixed =
            CallerAuthentication(
                Caller(UUID.fromString("77777777-7777-4777-8777-777777777777"), "bob", false, null, grants, CallerScope.EDIT),
            )
        val first =
            DocumentWrite(
                document(revision = 4),
                4,
                emptyList(),
                amended = false,
                unchanged = false,
                space = SpaceReference(space.id, space.slug, "Agent Docs"),
            )
        every { documents.changeHeader(any(), documentId, any()) } returns first
        every { documents.create(any(), SpaceSlug("agent-docs"), any()) } returns first
        every { queries.open(any(), documentId) } returns DocumentView(current, moved, emptyList())

        mockMvc
            .perform(
                patch("/api/documents/{id}", documentId)
                    .principal(mixed)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"baseVersion":3,"title":"재시도 정책","opId":"op-header"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.permission").value("edit"))
            .andExpect(jsonPath("$.revision").value(4))
            .andExpect(jsonPath("$.space.id").value(space.id.value.toString()))
        mockMvc
            .perform(
                post("/api/spaces/{slug}/documents", "agent-docs")
                    .principal(mixed)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"path":"payments/retry","title":"재시도 설계","opId":"op-create"}"""),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.permission").value("edit"))
            .andExpect(jsonPath("$.space.slug").value("agent-docs"))
    }

    @Test
    fun `an invited commenter opens the document with comment permission for its space`() {
        every { queries.open(any(), documentId) } returns DocumentView(document(), space, emptyList())

        mockMvc
            .perform(get("/api/documents/{id}", documentId).principal(member))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.permission").value("comment"))
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
                        """{"baseVersion":3,"blocks":[{"id":"head01","type":"heading","level":1,"text":"재시도 설계"},""" +
                            """{"id":"para01","type":"paragraph","text":" 실패하면 **60초** 뒤에 다시 시도한다. "}],"inputTrace":${WebFixtures.inputTraceJson}}""",
                    ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.amended").value(true))
            .andDo(
                documented(
                    "documents-save",
                    pathParameters(parameterWithName("id").description("문서 ID")),
                    requestFields(
                        *WebFixtures.writeMetadataFields(),
                        fieldWithPath("baseVersion").description("마지막으로 읽거나 저장한 version. 다르면 본문 전체와 currentVersion을 담아 409"),
                        blocksSubsection("blocks"),
                        fieldWithPath("opId").optional().type(JsonFieldType.STRING).description("멱등 키"),
                    ),
                    responseFields(*writeFields()),
                ),
            )
    }

    @Test
    fun `a stale save answers 409 with the full current content and version`() {
        every { documents.save(webContext, documentId, 2, listOf(revised)) } throws
            StaleRevisionException(2, 3, document())

        mockMvc
            .perform(
                put("/api/documents/{id}/content", documentId)
                    .principal(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"baseVersion":2,"blocks":[{"id":"para01","type":"paragraph","text":"실패하면 **60초** 뒤에 다시 시도한다."}]}"""),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.currentVersion").value(3))
            .andExpect(jsonPath("$.currentDocument.blocks[1].text").value("실패하면 **30초** 뒤에 다시 시도한다."))
            .andDo(documented("documents-conflict", responseFields(*staleFields())))
    }

    @Test
    fun `finalize confirms pending edits and returns the current version`() {
        every { documents.finalizeEdits(webContext, documentId, 4) } returns
            DocumentWrite(document(revision = 4), 4, emptyList(), amended = false, unchanged = true)

        mockMvc
            .perform(post("/api/documents/{id}/finalize?baseVersion=4", documentId).principal(owner))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.revision").value(4))
            .andExpect(jsonPath("$.version").value(4))
            .andDo(
                documented(
                    "documents-finalize",
                    queryParameters(parameterWithName("baseVersion").description("이 탭이 마지막으로 저장한 콘텐츠 버전. 현재 작성자·버전이 일치하는 대기 입력만 확정")),
                    responseFields(*writeFields()),
                ),
            )
    }

    @Test
    fun `save and header changes require baseVersion`() {
        mockMvc
            .perform(
                put("/api/documents/{id}/content", documentId)
                    .principal(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"blocks":[]}"""),
            ).andExpect(status().isBadRequest)
        mockMvc
            .perform(
                patch("/api/documents/{id}", documentId)
                    .principal(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"x"}"""),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `edits apply guarded operations and can answer a thread`() {
        val thread = ThreadId(UUID.fromString("66666666-6666-4666-8666-666666666666"))
        val context = WriteContext(agentAccess, ChangeSource.REST, "op-2", "재시도 간격 수정")
        val hash = BlockMarkdown.hash(body)
        val operations = listOf(EditOperation.ReplaceText(body.id, hash, "30초", "60초"))
        every { documents.edit(context, documentId, operations, thread) } returns
            DocumentWrite(document(listOf(title, revised), revision = 4), 4, listOf(body.id), amended = false, unchanged = false)

        mockMvc
            .perform(
                post("/api/documents/{id}/edits", documentId)
                    .principal(agent)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"operations":[{"op":"replaceText","blockId":"para01","ifHash":"$hash","find":"30초","replace":"60초"}],""" +
                            """"answering":"$thread","opId":"op-2","summary":"재시도 간격 수정"}""",
                    ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.revision").value(4))
            .andDo(
                documented(
                    "documents-edit",
                    requestFields(
                        *WebFixtures.writeMetadataFields(),
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
    fun `replaceText rejects edits without ifHash`() {
        mockMvc
            .perform(
                post("/api/documents/{id}/edits", documentId)
                    .principal(agent)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"operations":[{"op":"replaceText","blockId":"para01","find":"30초","replace":"60초"}]}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value("operation 0: ifHash is required"))
    }

    @Test
    fun `a header change keeps fields that are left out and clears an explicit null`() {
        val change = HeaderChange("재시도 정책", DocumentPath("payments/retry-policy"), DocumentStatus.CONFIRMED, null, AssigneeChange(null), 3)
        every { documents.changeHeader(webContext, documentId, change) } returns
            DocumentWrite(
                document(revision = 4),
                4,
                emptyList(),
                amended = false,
                unchanged = false,
                marks = listOf(BlockMark(body.id, agentActor, 3, at)),
                space = SpaceReference(space.id, space.slug, "원래 스페이스 이름"),
            )
        every { queries.open(ownerAccess, documentId) } returns DocumentView(document(revision = 9), space, emptyList())

        mockMvc
            .perform(
                patch("/api/documents/{id}", documentId)
                    .principal(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"baseVersion":3,"title":"재시도 정책","path":"payments/retry-policy","status":"confirmed","assignee":null}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.revision").value(4))
            .andExpect(jsonPath("$.blockChanges[0].revision").value(3))
            .andExpect(jsonPath("$.space.name").value("원래 스페이스 이름"))
            .andDo(
                documented(
                    "documents-change-header",
                    requestFields(
                        *WebFixtures.writeMetadataFields(),
                        fieldWithPath("baseVersion").description("마지막으로 본 version. 그 뒤 문서가 바뀌었으면 409"),
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
            StaleRevisionException(2, 3, document())

        mockMvc
            .perform(
                patch(
                    "/api/documents/{id}",
                    documentId,
                ).principal(owner).contentType(MediaType.APPLICATION_JSON).content("""{"baseVersion":2,"title":"x"}"""),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("conflict"))
            .andDo(documented("documents-stale-revision", responseFields(*staleFields())))
    }

    @Test
    fun `a move onto a path in use answers 409 naming the path, which may belong to a document moved along`() {
        every { documents.changeHeader(webContext, documentId, HeaderChange(null, DocumentPath("billing"), null, null, null, 3)) } throws
            PathTakenException(DocumentPath("billing/retry"))

        mockMvc
            .perform(
                patch(
                    "/api/documents/{id}",
                    documentId,
                ).principal(owner).contentType(MediaType.APPLICATION_JSON).content("""{"baseVersion":3,"path":"billing"}"""),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("taken"))
            .andExpect(jsonPath("$.path").value("billing/retry"))
            .andDo(
                documented(
                    "documents-path-taken",
                    responseFields(*workspaceProblemFields(), fieldWithPath("path").description("이미 다른 문서가 쓰고 있는 경로")),
                ),
            )
    }

    @Test
    fun `trash, untrash, and permanent delete answer 204 and take an opId for retries`() {
        every { documents.trash(webContext.copy(operationId = "op-trash"), documentId) } returns
            DocumentWrite(document(), 4, emptyList(), amended = false, unchanged = false)
        every { documents.untrash(webContext.copy(operationId = "op-untrash"), documentId) } returns
            DocumentWrite(document(), 5, emptyList(), amended = false, unchanged = false)
        every { documents.purge(webContext.copy(operationId = "op-purge"), documentId) } just runs

        mockMvc
            .perform(
                post("/api/documents/{id}/trash", documentId).param("opId", "op-trash").principal(owner),
            ).andExpect(status().isNoContent)
            .andDo(documented("documents-trash", queryParameters(opIdParameter())))
        mockMvc
            .perform(
                post("/api/documents/{id}/untrash", documentId).param("opId", "op-untrash").principal(owner),
            ).andExpect(status().isNoContent)
            .andDo(documented("documents-untrash", queryParameters(opIdParameter())))
        mockMvc
            .perform(
                delete("/api/documents/{id}", documentId).param("opId", "op-purge").principal(owner),
            ).andExpect(status().isNoContent)
            .andDo(documented("documents-purge", queryParameters(opIdParameter())))
    }

    private fun opIdParameter() = parameterWithName("opId").optional().description("재시도해도 한 번만 적용할 작업 ID")

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

    @Test
    fun `legacy operations report the successful record without suggesting a duplicate write`() {
        every { documents.save(any(), documentId, 3, any()) } throws
            LegacyOperationException(OperationRecord("document.save", documentId.value, 2))

        mockMvc
            .perform(
                put("/api/documents/{id}/content", documentId)
                    .principal(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"baseVersion":3,"blocks":[],"opId":"before-upgrade"}"""),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("legacy_operation"))
            .andExpect(jsonPath("$.target").value(documentId.toString()))
            .andExpect(jsonPath("$.revision").value(2))
            .andExpect(jsonPath("$.fix").value(org.hamcrest.Matchers.startsWith("Do not resend the original request.")))
            .andDo(
                documented(
                    "documents-legacy-operation",
                    responseFields(
                        *workspaceProblemFields(),
                        fieldWithPath("target").description("이미 성공한 작업의 대상 ID"),
                        fieldWithPath("revision").optional().type(JsonFieldType.NUMBER).description("기록에 남은 리비전. 작업에 따라 null"),
                    ),
                ),
            )
    }

    @Test
    fun `deleted response content reports result removed without inviting a duplicate write`() {
        every { documents.save(any(), documentId, 3, any()) } throws
            ResultRemovedException(OperationRecord("document.save", documentId.value, 2, state = OperationResultState.REMOVED))

        mockMvc
            .perform(
                put("/api/documents/{id}/content", documentId)
                    .principal(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"baseVersion":3,"blocks":[],"opId":"removed-result"}"""),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("result_removed"))
            .andExpect(jsonPath("$.target").value(documentId.toString()))
            .andExpect(jsonPath("$.revision").value(2))
            .andExpect(jsonPath("$.fix").value(org.hamcrest.Matchers.startsWith("Do not resend the original request.")))
            .andDo(
                documented(
                    "documents-result-removed",
                    responseFields(
                        *workspaceProblemFields(),
                        fieldWithPath("target").description("이미 성공한 작업의 대상 ID"),
                        fieldWithPath("revision").optional().type(JsonFieldType.NUMBER).description("기록에 남은 리비전. 작업에 따라 null"),
                    ),
                ),
            )
    }

    private fun staleFields(): Array<FieldDescriptor> =
        arrayOf(
            *workspaceProblemFields(),
            fieldWithPath("currentVersion").description("현재 내용 버전. 로컬 병합 후 baseVersion으로 보낸다"),
            subsectionWithPath("currentDocument").description("현재 문서의 헤더, version, 확정 revision과 전체 blocks"),
        )

    private fun listingFields(prefix: String): Array<FieldDescriptor> =
        arrayOf(
            fieldWithPath("${prefix}id").description("문서 ID"),
        ) + headerFields(prefix) + actorFields("${prefix}updatedBy")

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
            fieldWithPath("permission").description(PERMISSION),
        )

    private fun writeFields(): Array<FieldDescriptor> =
        arrayOf(
            fieldWithPath("id").description("문서 ID"),
            fieldWithPath("revision").description("마지막 확정 리비전. pending 저장은 확정할 때만 증가한다"),
            fieldWithPath("version").description("현재 내용 버전. 실제 변경마다 증가한다"),
            fieldWithPath("amended").description("같은 사람의 WEB 저장이 기존 pending에 이어졌는지. 유휴 30초 또는 총 600초면 확정한다"),
            fieldWithPath("unchanged").description("바뀐 것이 없어 리비전을 만들지 않았는지"),
            fieldWithPath("changedBlockIds").description("바뀐 블록 ID"),
            fieldWithPath("merged").description("항상 false. 서버는 오래된 version을 병합하지 않는다"),
            blocksSubsection("blocks"),
        )
}
