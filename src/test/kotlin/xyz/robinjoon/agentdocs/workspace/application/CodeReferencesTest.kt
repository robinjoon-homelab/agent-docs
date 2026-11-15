package xyz.robinjoon.agentdocs.workspace.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.application.model.CodeReferenceRequest
import xyz.robinjoon.agentdocs.workspace.application.model.InvalidRequestException
import xyz.robinjoon.agentdocs.workspace.application.service.CodeReferences
import xyz.robinjoon.agentdocs.workspace.domain.space.ExternalLink
import xyz.robinjoon.agentdocs.workspace.domain.space.ExternalLinkKind

class CodeReferencesTest {
    private val repo = ExternalLink.of(ExternalLinkKind.GITHUB_REPOSITORY, "owner/repo")

    @Test
    fun `a sole repository is inferred while multiple or missing repositories require an explicit one`() {
        val request = CodeReferenceRequest(null, "A".repeat(40), null)
        assertThat(CodeReferences.resolve(listOf(request), listOf(repo)).single().repository).isEqualTo("owner/repo")
        assertThatThrownBy { CodeReferences.resolve(listOf(request), emptyList()) }.isInstanceOf(InvalidRequestException::class.java)
        assertThatThrownBy {
            CodeReferences.resolve(listOf(request), listOf(repo, ExternalLink.of(ExternalLinkKind.GITHUB_REPOSITORY, "owner/other")))
        }.isInstanceOf(InvalidRequestException::class.java)
    }

    @Test
    fun `an explicit repository under an organization produces the specified pr link`() {
        val org = ExternalLink.of(ExternalLinkKind.GITHUB_ORGANIZATION, "owner")
        val resolved = CodeReferences.resolve(listOf(CodeReferenceRequest("owner/other", null, 4)), listOf(org)).single()
        assertThat(resolved.pullRequestUrl).isEqualTo("https://github.com/owner/other/pull/4")
    }
}
