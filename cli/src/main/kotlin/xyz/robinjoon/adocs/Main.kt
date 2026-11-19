package xyz.robinjoon.adocs

import xyz.robinjoon.adocs.adapter.inbound.console.Console
import java.io.IOException
import java.util.concurrent.TimeoutException

fun main(args: Array<String>) {
    val json = "--json" in args
    try {
        val arguments = Arguments(args.toList())
        if (arguments.positionals.isEmpty() || arguments.positionals.first() == "help") {
            Console.output(helpOutput(json))
            return
        }
        val host =
            Host(
                arguments.option("host") ?: System.getenv("ADOCS_HOST")
                    ?: throw CliFailure("host_required", "Set ADOCS_HOST or provide --host https://HOST"),
            )
        val result = Commands(host).run(arguments, Console::instruction)
        Console.output(if (json) Json.write(result) else Json.mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result))
    } catch (failure: CliFailure) {
        fail(failure, json)
    } catch (exception: IllegalArgumentException) {
        fail(CliFailure("invalid_input", "Check command arguments and block annotations; run adocs help", cause = exception), json)
    } catch (exception: IOException) {
        fail(
            CliFailure("io_failed", "Check local files and network; retry push with the same file to preserve opId", cause = exception),
            json,
        )
    } catch (exception: TimeoutException) {
        fail(CliFailure("login_timeout", "Run adocs login again", cause = exception), json)
    } catch (exception: tools.jackson.core.JacksonException) {
        fail(CliFailure("invalid_json", "Check JSON input or server response", cause = exception), json)
    }
}

private fun helpOutput(json: Boolean): String =
    if (json) {
        Json.write(
            mapOf("commands" to CommandRegistry.routes.keys + listOf("login", "logout", "pull", "push", "upload", "import-folder", "api")),
        )
    } else {
        help()
    }

private fun fail(
    failure: CliFailure,
    json: Boolean,
): Nothing {
    Console.error(failure.output(json))
    Console.finish(if (failure.status == 409) 3 else 1)
}

private fun help(): String =
    """
    adocs COMMAND [ID] [FILE] --host https://HOST [--json]
    login | logout | pull DOCUMENT FILE | push FILE | upload FILE [--space SLUG] [--opId KEY]
    ${CommandRegistry.routes.keys.joinToString(" | ")}
    import-folder DIRECTORY --tool TOOL [--space SLUG]
    REST actions: --body JSON_FILE, --query 'key=value&key=value', --space SLUG.
    api METHOD /api/PATH [--body JSON_FILE] exposes additional server actions.
    ADOCS_HOST supplies host; ADOCS_TOKEN supplies a script token instead of OAuth.
    pull creates a new file. After a push conflict, pull the same document and file to back up your edits
    and load the latest revision. Merge the edits you want from the backup and push again.
    Keep all b/h/cols and row ID comments when editing.
    push sends one atomic batch, at most 200 edits. Use header for front matter changes.
    Exit status: 0 success, 3 HTTP 409 conflict, 1 other error. No automatic write retries.
    """.trimIndent()
