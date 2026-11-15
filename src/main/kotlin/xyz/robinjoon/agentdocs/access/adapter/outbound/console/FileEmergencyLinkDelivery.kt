package xyz.robinjoon.agentdocs.access.adapter.outbound.console

import xyz.robinjoon.agentdocs.access.application.port.output.EmergencyLinkDelivery
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions

// A new private file is the only secret delivery boundary; never log the link.
class FileEmergencyLinkDelivery(
    private val destination: Path,
) : EmergencyLinkDelivery {
    override fun deliver(link: String) {
        Files
            .newByteChannel(
                destination,
                setOf(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")),
            ).use { channel ->
                val buffer = java.nio.ByteBuffer.wrap((link + "\n").toByteArray())
                while (buffer.hasRemaining()) channel.write(buffer)
            }
    }
}
