package xyz.robinjoon.agentdocs.workspace.adapter.outbound.render

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import xyz.robinjoon.agentdocs.workspace.application.model.RenderUnavailableException
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.deleteIfExists

/** Chromium's anonymous DevTools pipes are inherited on descriptors 3 and 4; there is no listening port. */
internal class ChromiumSession(
    private val process: Process,
    private val json: ObjectMapper,
) {
    private val input = process.inputStream.buffered()
    private var next = 0
    var session: String? = null

    fun call(
        method: String,
        params: Map<String, Any?> = emptyMap(),
    ): JsonNode {
        val id = ++next
        val command = mutableMapOf<String, Any?>("id" to id, "method" to method, "params" to params)
        session?.let { command["sessionId"] = it }
        process.outputStream.write(json.writeValueAsBytes(command))
        process.outputStream.write(0)
        process.outputStream.flush()
        while (true) {
            val message = receive()
            if (message.path("id").asInt(-1) != id) continue
            if (message.has("error")) throw RenderUnavailableException("Chromium could not complete $method")
            return message.path("result")
        }
    }

    private fun receive(): JsonNode {
        val frame = ByteArrayOutputStream()
        while (frame.size() < MAX_FRAME_BYTES) {
            val byte = input.read()
            if (byte == 0) return json.readTree(frame.toByteArray())
            if (byte < 0) throw EOFException("Render process ended or exceeded its deadline")
            frame.write(byte)
        }
        throw RenderUnavailableException("Chromium render result exceeded its size limit")
    }

    companion object {
        private const val MAX_FRAME_BYTES = 14 * 1024 * 1024
        private const val TIMEOUT_SECONDS = 25L
        private val flags =
            listOf(
                "--headless",
                "--remote-debugging-pipe",
                "--disable-gpu",
                "--no-first-run",
                "--no-default-browser-check",
                "--disable-component-update",
                "--disable-sync",
                "--disable-extensions",
                "--disable-background-networking",
                "--disable-breakpad",
                "--disable-dev-shm-usage",
                "--mute-audio",
                "--proxy-server=127.0.0.1:9",
                "--proxy-bypass-list=<-loopback>",
                "--host-resolver-rules=MAP * ~NOTFOUND",
                "--window-size=1440,900",
            )

        fun <T> run(
            binary: String,
            json: ObjectMapper,
            work: (ChromiumSession) -> T,
        ): T {
            val directory = Files.createTempDirectory("agent-docs-design-")
            try {
                val process = launch(binary, directory)
                // The watcher also bounds a hung command when the browser emits no reply.
                val watcher = Thread.startVirtualThread { if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) stop(process) }
                try {
                    return work(ChromiumSession(process, json))
                } finally {
                    stop(process)
                    watcher.join()
                }
            } catch (error: IOException) {
                throw RenderUnavailableException("Chromium render failed; check the configured browser and its sandbox", error)
            } finally {
                Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Path::deleteIfExists) }
            }
        }

        private fun launch(
            binary: String,
            directory: Path,
        ): Process {
            val prefix = listOf("/bin/sh", "-c", """exec "$@" 3<&0 4>&1 </dev/null >/dev/null""", "chromium", binary)
            val process =
                ProcessBuilder(prefix + flags + listOf("--user-data-dir=" + directory.resolve("profile"), "about:blank"))
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
            process.environment().clear()
            process.environment().putAll(mapOf("HOME" to directory.toString(), "PATH" to "/usr/bin:/bin", "LANG" to "C.UTF-8"))
            return process.start()
        }

        private fun stop(process: Process) {
            process.descendants().use { descendants -> descendants.forEach { it.destroyForcibly() } }
            process.destroyForcibly()
            process.waitFor()
        }
    }
}
