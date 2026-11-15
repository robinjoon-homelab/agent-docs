package xyz.robinjoon.adocs

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions

internal object PrivateFiles {
    private val fileMode = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))
    private val directoryMode = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))

    fun directory(path: Path): Path {
        Files.createDirectories(path, directoryMode)
        require(!Files.isSymbolicLink(path)) { "private directory cannot be a symbolic link" }
        Files.setPosixFilePermissions(path, directoryMode.value())
        return path
    }

    fun write(
        path: Path,
        text: String,
    ) {
        directory(path.parent)
        val temporary = Files.createTempFile(path.parent, ".adocs-", ".tmp", fileMode)
        try {
            Files.writeString(temporary, text)
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    fun <T> locked(
        path: Path,
        action: () -> T,
    ): T {
        directory(path.parent)
        val options = setOf(StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)
        return FileChannel.open(path, options, fileMode).use { channel ->
            channel.lock().use { action() }
        }
    }

    fun userRoot(
        variable: String,
        fallback: String,
    ): Path = Path.of(System.getenv(variable) ?: Path.of(System.getProperty("user.home"), fallback).toString(), "adocs")
}
