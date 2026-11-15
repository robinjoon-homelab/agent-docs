package xyz.robinjoon.adocs

internal class Arguments(
    args: List<String>,
) {
    private val options = linkedMapOf<String, String>()
    val positionals: List<String>
    val json: Boolean = "--json" in args

    init {
        val positional = arrayListOf<String>()
        var index = 0
        while (index < args.size) {
            val arg = args[index++]
            when {
                arg == "--json" -> {
                    Unit
                }

                arg.startsWith("--") -> {
                    require(index < args.size && !args[index].startsWith("--")) { "option needs a value" }
                    require(options.put(arg.drop(2), args[index++]) == null) { "duplicate option" }
                }

                else -> {
                    positional += arg
                }
            }
        }
        positionals = positional.toList()
    }

    fun option(name: String): String? = options[name]

    fun required(name: String): String = option(name) ?: throw CliFailure("missing_option", "Provide --$name VALUE")

    fun argument(index: Int): String =
        positionals.getOrNull(index) ?: throw CliFailure("missing_argument", "Run adocs help for command syntax")
}
