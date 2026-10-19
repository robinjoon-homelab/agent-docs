package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.support.persistence.MigratedH2Database
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.at
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.person
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.space
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.asset.Asset
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetContent
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetMediaType
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetOrigin
import java.util.UUID

class ExposedAssetRepositoryTest {
    private val database = MigratedH2Database()
    private val repository = ExposedAssetRepository()
    private val spaces = ExposedSpaceRepository()
    private val content = AssetContent.of(AssetMediaType.WEBP, byteArrayOf(9, 8, 7))

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `an asset round trips and several assets may share the same bytes`() {
        val docs = database.inTransaction { spaces.add(space("docs")) }
        val first = asset(docs.id, "https://example.com/a.webp")
        val again = asset(docs.id, "https://mirror.example.com/a.webp")

        database.inTransaction { repository.add(first) }
        database.inTransaction { repository.add(again) }

        assertThat(database.inTransaction { repository.findById(first.id) }).isEqualTo(first)
        assertThat(
            database.inTransaction { repository.findById(again.id) }?.origin?.sourceUrl,
        ).isEqualTo("https://mirror.example.com/a.webp")
    }

    private fun asset(
        space: SpaceId,
        sourceUrl: String?,
    ): Asset = Asset.upload(AssetId(UUID.randomUUID()), space, content, AssetOrigin("a.webp", sourceUrl, person, at))
}
