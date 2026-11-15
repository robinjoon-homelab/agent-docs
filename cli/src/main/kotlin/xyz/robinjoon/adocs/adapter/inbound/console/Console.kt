package xyz.robinjoon.adocs.adapter.inbound.console

// The CLI's external contract owns stdout, stderr, and process status. Server code must never use this adapter.
internal object Console {
    fun output(text: String) {
        System.out.println(text)
    }

    fun error(text: String) {
        System.err.println(text)
    }

    fun instruction(url: String) {
        System.err.println("Open this authorization address in your browser: $url")
    }

    fun finish(status: Int): Nothing = kotlin.system.exitProcess(status)
}
