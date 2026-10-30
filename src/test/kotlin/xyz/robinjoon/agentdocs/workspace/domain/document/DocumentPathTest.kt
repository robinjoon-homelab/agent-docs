package xyz.robinjoon.agentdocs.workspace.domain.document

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class DocumentPathTest {
    @Test
    fun `the leading segments name the parent document and every document above it`() {
        val path = DocumentPath("기획/결제/재시도")

        assertThat(path.parent).isEqualTo(DocumentPath("기획/결제"))
        assertThat(path.ancestors).containsExactly(DocumentPath("기획"), DocumentPath("기획/결제"))
        assertThat(DocumentPath("기획").parent).isNull()
        assertThat(DocumentPath("기획").ancestors).isEmpty()
    }

    @Test
    fun `a document is within itself and its ancestors but not within a sibling that shares a prefix`() {
        val path = DocumentPath("payments/retry")

        assertThat(path.isWithin(DocumentPath("payments"))).isTrue()
        assertThat(path.isWithin(path)).isTrue()
        assertThat(path.isWithin(DocumentPath("pay"))).isFalse()
        assertThat(DocumentPath("payments").isWithin(path)).isFalse()
    }

    @Test
    fun `moving an ancestor keeps the rest of the path`() {
        val moved = DocumentPath("payments/retry/backoff").movedFrom(DocumentPath("payments"), DocumentPath("billing/old"))

        assertThat(moved).isEqualTo(DocumentPath("billing/old/retry/backoff"))
    }

    @Test
    fun `a move that would make a path too deep is refused`() {
        val deep = DocumentPath((1..10).joinToString("/") { "s$it" })

        assertThatThrownBy { deep.movedFrom(DocumentPath("s1"), DocumentPath("a/b")) }.isInstanceOf(InvalidDocumentException::class.java)
        assertThatThrownBy { deep.movedFrom(DocumentPath("other"), DocumentPath("a")) }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
