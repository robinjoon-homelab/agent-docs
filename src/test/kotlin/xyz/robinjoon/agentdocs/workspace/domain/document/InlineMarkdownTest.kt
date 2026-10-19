package xyz.robinjoon.agentdocs.workspace.domain.document

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class InlineMarkdownTest {
    // The expected values are what the web editor's plainText returns for the same input. Text anchors are offsets into
    // this string on both sides, so the frontend test lists the same cases.
    @ParameterizedTest
    @MethodSource("editorPlainText")
    fun `plain text is exactly what the web editor shows`(
        markdown: String,
        expected: String,
    ) {
        assertThat(InlineMarkdown.plainText(markdown)).isEqualTo(expected)
    }

    companion object {
        @JvmStatic
        fun editorPlainText(): List<Arguments> =
            listOf(
                Arguments.of("**bold** and *italic* and ~~gone~~", "bold and italic and gone"),
                Arguments.of("run `./gradlew build` first", "run ./gradlew build first"),
                Arguments.of("see [the ADR](https://example.com/adr)", "see the ADR"),
                Arguments.of("![diagram](asset:1234) caption", "!diagram caption"),
                Arguments.of("2 * 3 and a ** b", "2 * 3 and a ** b"),
                Arguments.of("*unclosed emphasis", "*unclosed emphasis"),
                Arguments.of("literal \\*star\\* and \\[bracket]", "literal *star* and [bracket]"),
                Arguments.of("[not a link] and `unclosed", "[not a link] and `unclosed"),
                Arguments.of("[**bold link**](x)", "bold link"),
                Arguments.of("*foo**bar*", "foo**bar"),
                Arguments.of("**foo*bar**", "foo*bar"),
                Arguments.of("*foo**bar**baz*", "foobarbaz"),
                Arguments.of("***a**b*", "ab"),
                Arguments.of("a**b**c***d***", "abcd"),
                Arguments.of("foo*bar*", "foobar"),
                Arguments.of("* not emphasis *", "* not emphasis *"),
                Arguments.of("**a *b* c**", "a b c"),
                Arguments.of("a\\qb and \\\\ and \\", "a\\qb and \\ and \\"),
                Arguments.of("`` a ` b `` and ` `", "a ` b and  "),
                Arguments.of("x<BR>y<br  />z<br\t/>w<bra>", "x\ny\nz\nw<bra>"),
                Arguments.of("~single~ and ~~~three~~~ and ~~two~~", "~single~ and ~~~three~~~ and two"),
                Arguments.of("_under_ stays", "_under_ stays"),
                Arguments.of("[outer [inner](a) text](b)", "outer [inner](a) text"),
                Arguments.of("[a](<dest with space>) and [b](c(d)e)", "a and b"),
                Arguments.of("[a](b c) and [x](y", "[a](b c) and [x](y"),
                Arguments.of("[link `code]` x](y)", "link code] x"),
                Arguments.of("line\nbreak", "line\nbreak"),
                Arguments.of("`code with *star*` and *em*", "code with *star* and em"),
                Arguments.of("***", "***"),
                Arguments.of("**a**b**", "ab**"),
                Arguments.of("\u00A0*nbsp*\u00A0", "\u00A0nbsp\u00A0"),
            )
    }
}
