package xyz.robinjoon.agentdocs.workspace.adapter.inbound.startup

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.springframework.boot.DefaultApplicationArguments
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentPath
import xyz.robinjoon.agentdocs.workspace.domain.document.PathTakenException

class FolderBackfillTest {
    private val documents = mockk<DocumentUseCase>()

    @Test
    fun `a folder path another instance took first does not stop the application from starting`() {
        every { documents.fillMissingParents("robin") } throws PathTakenException(DocumentPath("기획"))

        assertThatCode { FolderBackfill(documents, "robin").run(DefaultApplicationArguments()) }.doesNotThrowAnyException()
    }
}
