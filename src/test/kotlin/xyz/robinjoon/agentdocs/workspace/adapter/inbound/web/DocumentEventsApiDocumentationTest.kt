package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import xyz.robinjoon.agentdocs.application.model.CurrentCallerSnapshot
import xyz.robinjoon.agentdocs.application.port.input.CurrentCallerUseCase
import xyz.robinjoon.agentdocs.support.web.RestDocsWebTest
import xyz.robinjoon.agentdocs.support.web.documented
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentEventSnapshot
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentEventsUseCase
import java.util.UUID
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture

class DocumentEventsApiDocumentationTest : RestDocsWebTest() {
    private val snapshots = mockk<DocumentEventsUseCase>()
    private val auth = mockk<CurrentCallerUseCase>()
    private val scheduler = mockk<ScheduledExecutorService>()
    private val controller = DocumentEventsController(snapshots, DocumentEventCaller(auth), scheduler)
    private val mvc = restDocsMockMvc(controller, DocumentEventExceptionHandler())

    @Test
    fun `stream is async without proxy buffering or browser caching`() {
        every { auth.current(any()) } returns
            CurrentCallerSnapshot(
                UUID.randomUUID(),
                "owner",
                null,
                null,
                CurrentCallerSnapshot.Scope.OWNER,
            )
        every { snapshots.snapshot(any(), any()) } returns DocumentEventSnapshot(WebFixtures.spaceId, 1, 1, false, "state")
        every { scheduler.scheduleWithFixedDelay(any<Runnable>(), any(), any(), any()) } returns mockk<ScheduledFuture<*>>()
        mvc
            .perform(get("/api/documents/{id}/events", UUID.randomUUID()).header("Authorization", "Bearer test"))
            .andExpect(status().isOk)
            .andExpect(request().asyncStarted())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("X-Accel-Buffering", "no"))
            .andDo(
                documented(
                    "document-events",
                    pathParameters(
                        parameterWithName("id").description("문서 ID. invalidate 이벤트마다 기존 문서·댓글 API로 재조회. heartbeat는 주석이며 재연결은 최신 상태로 수렴."),
                    ),
                ),
            )
    }

    @Test
    fun `expired credentials reject a new connection`() {
        every { auth.current(any()) } returns null
        mvc
            .perform(get("/api/documents/{id}/events", UUID.randomUUID()).header("Authorization", "Bearer expired"))
            .andExpect(status().isUnauthorized)
    }
}
