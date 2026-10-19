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
    fun `an asset round trips and the same bytes in a space resolve to the first asset`() {
        val docs = database.inTransaction { spaces.add(space("docs")) }
        val notes = database.inTransaction { spaces.add(space("notes")) }
        val first = asset(docs.id, "https://example.com/a.webp")
        database.inTransaction { repository.add(first) }

        val again = database.inTransaction { repository.add(asset(docs.id, null)) }
        val elsewhere = database.inTransaction { repository.add(asset(notes.id, null)) }

        assertThat(database.inTransaction { repository.findById(first.id) }).isEqualTo(first)
        assertThat(again).isEqualTo(first)
        assertThat(elsewhere.id).isNotEqualTo(first.id)
        assertThat(database.inTransaction { repository.findByContent(notes.id, content.sha256) }).isEqualTo(elsewhere)
    }

    private fun asset(
        space: SpaceId,
        sourceUrl: String?,
    ): Asset = Asset.upload(AssetId(UUID.randomUUID()), space, content, AssetOrigin("a.webp", sourceUrl, person, at))
}
