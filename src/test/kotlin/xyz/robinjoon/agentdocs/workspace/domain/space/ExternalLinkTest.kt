package xyz.robinjoon.agentdocs.workspace.domain.space

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ExternalLinkTest {
    @ParameterizedTest
    @ValueSource(
        strings = [
            "homelab/agent-docs",
            "https://github.com/Homelab/Agent-Docs.git",
            "https://github.com/homelab/agent-docs/",
            "git@github.com:homelab/agent-docs.git",
            "ssh://git@github.com/homelab/agent-docs",
        ],
    )
    fun `a pasted git remote normalizes to the lowercase owner and repository`(remote: String) {
        assertThat(ExternalLink.repositoryOf(remote)).isEqualTo("homelab/agent-docs")
        assertThat(ExternalLink.of(ExternalLinkKind.GITHUB_REPOSITORY, remote).value).isEqualTo("homelab/agent-docs")
    }

    @ParameterizedTest
    @ValueSource(strings = ["homelab", "homelab/agent-docs/wiki", "https://gitlab.com/homelab/agent-docs", "owner/re po"])
    fun `anything that is not one GitHub repository is rejected`(remote: String) {
        assertThat(ExternalLink.repositoryOf(remote)).isNull()
        assertThatThrownBy { ExternalLink.of(ExternalLinkKind.GITHUB_REPOSITORY, remote) }
            .isInstanceOf(InvalidSpaceException::class.java)
            .hasMessage("a repository must look like owner/repo")
    }

    @Test
    fun `an organization link matches every repository of the organization and nothing else`() {
        val organization = ExternalLink.of(ExternalLinkKind.GITHUB_ORGANIZATION, "https://github.com/Homelab/")

        assertThat(organization.value).isEqualTo("homelab")
        assertThat(organization.matchesRepository("homelab/agent-docs")).isTrue()
        assertThat(organization.matchesRepository("homelab-tools/agent-docs")).isFalse()
    }

    @Test
    fun `a repository link matches only that repository`() {
        val repository = ExternalLink.of(ExternalLinkKind.GITHUB_REPOSITORY, "homelab/agent-docs")

        assertThat(repository.matchesRepository("homelab/agent-docs")).isTrue()
        assertThat(repository.matchesRepository("homelab/scraping-platform")).isFalse()
    }

    @Test
    fun `a path link covers the directory and below but not a sibling sharing its prefix`() {
        val path = ExternalLink.of(ExternalLinkKind.LOCAL_PATH, "/Users/me/IdeaProjects/agent-docs/")

        assertThat(path.value).isEqualTo("/Users/me/IdeaProjects/agent-docs")
        assertThat(path.coversPath("/Users/me/IdeaProjects/agent-docs")).isTrue()
        assertThat(path.coversPath("/Users/me/IdeaProjects/agent-docs/frontend")).isTrue()
        assertThat(path.coversPath("/Users/me/IdeaProjects/agent-docs-old")).isFalse()
    }

    @Test
    fun `relative paths and URLs without a web scheme are rejected`() {
        assertThatThrownBy { ExternalLink.of(ExternalLinkKind.LOCAL_PATH, "IdeaProjects/agent-docs") }
            .isInstanceOf(InvalidSpaceException::class.java)
            .hasMessage("a local path must be absolute, starting with /")
        assertThatThrownBy { ExternalLink.of(ExternalLinkKind.LOCAL_PATH, "~/IdeaProjects/agent-docs") }
            .isInstanceOf(InvalidSpaceException::class.java)
        assertThatThrownBy { ExternalLink.of(ExternalLinkKind.URL, "ftp://example.com") }
            .isInstanceOf(InvalidSpaceException::class.java)
            .hasMessage("a URL must start with http:// or https://")
    }
}
