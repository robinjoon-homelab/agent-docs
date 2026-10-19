package xyz.robinjoon.agentdocs.workspace.domain.asset

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.domain.Actor
import java.time.Instant
import java.util.UUID

class AssetContentTest {
    @Test
    fun `content is identified by the SHA-256 of its bytes`() {
        val content = AssetContent.of(AssetMediaType.PNG, "abc".toByteArray())

        assertThat(content.sha256).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
        assertThat(content.size).isEqualTo(3)
    }

    @Test
    fun `an empty file is refused`() {
        assertThatThrownBy { AssetContent.of(AssetMediaType.PNG, ByteArray(0)) }
            .isInstanceOf(InvalidAssetException::class.java)
            .hasMessage("a file must be 1 byte to 20MB")
    }

    @Test
    fun `a file name cannot carry a path`() {
        assertThatThrownBy { AssetOrigin("../etc/passwd", null, Actor.human(UUID.randomUUID(), "robin"), Instant.EPOCH) }
            .isInstanceOf(InvalidAssetException::class.java)
    }

    @Test
    fun `a media type is recognized with or without parameters`() {
        assertThat(AssetMediaType.of("image/SVG+xml; charset=utf-8")).isEqualTo(AssetMediaType.SVG)
        assertThat(AssetMediaType.of("application/pdf")).isNull()
    }
}
