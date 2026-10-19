package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.mcp

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import java.time.Instant
import java.util.UUID

class ArgumentsTest {
    private val json = JsonMapper.builder().build()

    @ParameterizedTest
    @ValueSource(strings = ["1", "true", "[]", "{}"])
    fun `optional strings reject other JSON types`(value: String) {
        val arguments = arguments(value)

        assertThatThrownBy { arguments.optionalString("value") }
            .isInstanceOf(InvalidRequestException::class.java)
            .hasMessage("argument value must be a string")
        assertThatThrownBy { arguments.optionalUuid("value") }
            .isInstanceOf(InvalidRequestException::class.java)
            .hasMessage("argument value must be a string")
        assertThatThrownBy { arguments.optionalInstant("value") }
            .isInstanceOf(InvalidRequestException::class.java)
            .hasMessage("argument value must be a string")
    }

    @ParameterizedTest
    @ValueSource(strings = ["\"1\"", "1.0", "1.5", "true", "[]", "{}", "2147483648", "-2147483649"])
    fun `optional integers reject other types and values outside the integer range`(value: String) {
        assertThatThrownBy { arguments(value).optionalInt("value") }
            .isInstanceOf(InvalidRequestException::class.java)
            .hasMessage("argument value must be an integer")
    }

    @ParameterizedTest
    @ValueSource(strings = ["\"true\"", "\"false\"", "0", "1", "[]", "{}"])
    fun `booleans reject values that would otherwise be coerced`(value: String) {
        assertThatThrownBy { arguments(value).boolean("value") }
            .isInstanceOf(InvalidRequestException::class.java)
            .hasMessage("argument value must be a boolean")
    }

    @ParameterizedTest
    @ValueSource(strings = ["\"resolved\"", "1", "true", "{}", "[\"resolved\",1]", "[null]", "[true]", "[[]]", "[{}]"])
    fun `string arrays reject other types and every non-string element`(value: String) {
        assertThatThrownBy { arguments(value).strings("value") }
            .isInstanceOf(InvalidRequestException::class.java)
            .hasMessage("argument value must be an array of strings")
    }

    @ParameterizedTest
    @ValueSource(strings = ["{}", "{\"value\":null}"])
    fun `missing and null optional arguments keep their defaults`(body: String) {
        val arguments = Arguments(json.readTree(body))

        assertThat(arguments.optionalString("value")).isNull()
        assertThat(arguments.optionalUuid("value")).isNull()
        assertThat(arguments.optionalInstant("value")).isNull()
        assertThat(arguments.optionalInt("value")).isNull()
        assertThat(arguments.strings("value")).isNull()
        assertThat(arguments.boolean("value")).isFalse()
    }

    @Test
    fun `valid strings and empty strings retain their meaning`() {
        assertThat(arguments("\"text\"").optionalString("value")).isEqualTo("text")
        assertThat(arguments("\"\"").optionalString("value")).isNull()
        assertThat(arguments("\"\"").optionalUuid("value")).isNull()
        assertThat(arguments("\"\"").optionalInstant("value")).isNull()
        assertThat(arguments("\"11111111-1111-4111-8111-111111111111\"").optionalUuid("value"))
            .isEqualTo(UUID.fromString("11111111-1111-4111-8111-111111111111"))
        assertThat(arguments("\"2026-10-18T09:00:00Z\"").optionalInstant("value"))
            .isEqualTo(Instant.parse("2026-10-18T09:00:00Z"))
    }

    @ParameterizedTest
    @ValueSource(ints = [Int.MIN_VALUE, -1, 0, 1, Int.MAX_VALUE])
    fun `valid integers retain their exact value`(value: Int) {
        assertThat(arguments(value.toString()).optionalInt("value")).isEqualTo(value)
    }

    @Test
    fun `valid booleans and string arrays retain their exact value`() {
        assertThat(arguments("true").boolean("value")).isTrue()
        assertThat(arguments("false").boolean("value")).isFalse()
        assertThat(arguments("[]").strings("value")).isEmpty()
        assertThat(arguments("[\"resolved\",\"open\",\"\"]").strings("value")).containsExactly("resolved", "open", "")
    }

    private fun arguments(value: String): Arguments = Arguments(json.readTree("""{"value":$value}"""))
}
