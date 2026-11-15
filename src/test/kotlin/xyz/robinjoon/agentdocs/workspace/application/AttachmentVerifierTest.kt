package xyz.robinjoon.agentdocs.workspace.application

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.application.model.UploadedFile
import xyz.robinjoon.agentdocs.workspace.application.port.output.ImageVerifier
import xyz.robinjoon.agentdocs.workspace.application.service.AttachmentVerifier
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetMediaType
import xyz.robinjoon.agentdocs.workspace.domain.asset.InvalidAssetException

class AttachmentVerifierTest {
    private val images = mockk<ImageVerifier>()
    private val verifier = AttachmentVerifier(images)

    init {
        every { images.verify(any()) } returns null
    }

    @Test
    fun `HTML scripts are preserved as immutable original bytes`() {
        val bytes = "<!doctype html><html><script>document.body.textContent = '시안'</script></html>".toByteArray()
        val result = verifier.verify(UploadedFile("screen.html", "text/html", bytes))

        assertThat(result.mediaType).isEqualTo(AssetMediaType.HTML)
        assertThat(result.bytes).isEqualTo(bytes)
    }

    @Test
    fun `PDF content is recognized while arbitrary bytes become inert downloads`() {
        val pdf = verifier.verify(UploadedFile("a.pdf", "application/pdf", "%PDF-1.7\nbody".toByteArray()))
        val binary =
            verifier.verify(
                UploadedFile("a.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", byteArrayOf(1)),
            )

        assertThat(pdf.mediaType).isEqualTo(AssetMediaType.PDF)
        assertThat(binary.mediaType).isEqualTo(AssetMediaType.BINARY)
    }

    @Test
    fun `claimed executable text types and invalid UTF8 cannot bypass validation`() {
        assertThatThrownBy { verifier.verify(UploadedFile("fake.pdf", "application/pdf", byteArrayOf(1))) }
            .isInstanceOf(InvalidAssetException::class.java)
        assertThatThrownBy { verifier.verify(UploadedFile("fake.html", "text/html", byteArrayOf(0xff.toByte()))) }
            .isInstanceOf(InvalidAssetException::class.java)
        assertThatThrownBy { verifier.verify(UploadedFile("fake.png", "image/png", "<html></html>".toByteArray())) }
            .isInstanceOf(InvalidAssetException::class.java)
    }
}
