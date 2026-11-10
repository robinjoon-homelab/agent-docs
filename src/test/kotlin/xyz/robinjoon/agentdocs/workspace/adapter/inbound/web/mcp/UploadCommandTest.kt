package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import xyz.robinjoon.agentdocs.access.application.model.UploadToken
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit

// Runs the command in a real shell with a curl that only prints its arguments, because what matters is what a shell
// does with the text once an agent has put a path into it.
class UploadCommandTest(
    @param:TempDir private val dir: Path,
) {
    private val token = UploadToken.of(UUID.randomUUID(), "s3cret-_abc")
    private val endpoint = "https://docs.example.com/api/uploads"

    @Test
    fun `a file name cannot run anything in the agent's shell when the path is put in as it is`() {
        val canary = dir.resolve("canary")
        val names =
            listOf(
                "a'\$(touch $canary)'.png",
                "a'; touch $canary; '.png",
                "a`touch $canary`.png",
                "a\"\$(touch $canary)\".png",
                "a\ntouch $canary\n.png",
            )

        names.forEach { curlArguments("$dir/$it") }

        assertThat(canary).doesNotExist()
    }

    @ParameterizedTest
    @ValueSource(strings = ["a b.png", "한글 이미지.png", "a;type=text/html,b.png", "a\"b\\c.png", "it's.png"])
    fun `curl is given the whole path, quoted so that its own parsing leaves it alone`(name: String) {
        val path = "$dir/$name"
        val escaped = path.replace("\\", "\\\\").replace("\"", "\\\"")

        assertThat(curlArguments(path)).contains("file=@\"$escaped\"")
    }

    @Test
    fun `the token travels in a header and the address stays clean`() {
        val arguments = curlArguments("$dir/flow.png")

        assertThat(arguments).contains("Authorization: Bearer $token")
        assertThat(arguments.last()).isEqualTo(endpoint)
    }

    private fun curlArguments(path: String): List<String> {
        val bin = Files.createDirectories(dir.resolve("bin"))
        val curl = bin.resolve("curl")
        Files.writeString(curl, "#!/bin/sh\nfor argument in \"\$@\"; do printf '%s\\n' \"\$argument\"; done\n")
        curl.toFile().setExecutable(true)
        val builder = ProcessBuilder("sh").directory(dir.toFile()).redirectErrorStream(true)
        builder.environment()["PATH"] = "$bin:${System.getenv("PATH")}"
        val shell = builder.start()
        // The script goes in on stdin so a non-ASCII path does not depend on the JVM's encoding for arguments.
        shell.outputStream.use { it.write(UploadCommand.of(token, endpoint).replace(UploadCommand.PATH_SLOT, path).toByteArray()) }
        val output = shell.inputStream.readAllBytes().toString(Charsets.UTF_8)
        assertThat(shell.waitFor(10, TimeUnit.SECONDS)).isTrue()
        return output.lines().filter { it.isNotEmpty() }
    }
}
