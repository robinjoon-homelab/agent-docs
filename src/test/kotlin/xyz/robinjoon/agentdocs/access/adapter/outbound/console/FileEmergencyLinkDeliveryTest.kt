package xyz.robinjoon.agentdocs.access.adapter.outbound.console

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

class FileEmergencyLinkDeliveryTest(
    @param:TempDir private val directory: Path,
) {
    @Test
    fun `the link is delivered only to a new owner-only file`() {
        val destination = directory.resolve("link.txt")
        FileEmergencyLinkDelivery(destination).deliver("https://docs.example/emergency-login#fake-token")
        assertThat(Files.readString(destination)).isEqualTo("https://docs.example/emergency-login#fake-token\n")
        assertThat(Files.getPosixFilePermissions(destination)).isEqualTo(PosixFilePermissions.fromString("rw-------"))
        assertThatExceptionOfType(FileAlreadyExistsException::class.java).isThrownBy {
            FileEmergencyLinkDelivery(destination).deliver("replacement")
        }
        assertThat(Files.readString(destination)).contains("fake-token")
    }
}
