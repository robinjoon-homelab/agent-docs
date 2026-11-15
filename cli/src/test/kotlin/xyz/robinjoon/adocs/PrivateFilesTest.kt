package xyz.robinjoon.adocs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

class PrivateFilesTest {
    @Test
    fun `credentials are atomically replaced with private permissions`(
        @TempDir directory: Path,
    ) {
        val target = directory.resolve("host").resolve("credentials.json")
        PrivateFiles.write(target, "first")
        PrivateFiles.write(target, "second")
        assertEquals("second", Files.readString(target))
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(target)))
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(target.parent)))
    }
}
