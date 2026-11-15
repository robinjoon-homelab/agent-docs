package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.jdbc.insert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.support.persistence.MigratedH2Database
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.at
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.document
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.paragraph
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.person
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.revisionOf
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence.PersistenceFixtures.space
import xyz.robinjoon.agentdocs.workspace.domain.SpaceId
import xyz.robinjoon.agentdocs.workspace.domain.asset.Asset
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetContent
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetMediaType
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetOrigin
import xyz.robinjoon.agentdocs.workspace.domain.space.ExternalLink
import xyz.robinjoon.agentdocs.workspace.domain.space.ExternalLinkKind
import xyz.robinjoon.agentdocs.workspace.domain.space.SpaceProfile
import java.util.UUID

class ExposedExportCatalogTest {
    private val database = MigratedH2Database()
    private val catalog = ExposedExportCatalog()

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `code examples do not bring another spaces assets into an export`() {
        val selected = database.inTransaction { ExposedSpaceRepository().add(space("selected")) }
        val other = database.inTransaction { ExposedSpaceRepository().add(space("other")) }
        val external = asset(other.id, byteArrayOf(9))
        val code =
            xyz.robinjoon.agentdocs.workspace.domain.document.CodeBlock(
                xyz.robinjoon.agentdocs.workspace.domain.document
                    .BlockId("codeblock"),
                "text",
                "asset:${external.id.value}",
            )
        val sample = document(selected.id, "sample", listOf(code, paragraph("plainuuid", external.id.value.toString())))
        database.inTransaction {
            ExposedAssetRepository().add(external)
            ExposedDocumentRepository().add(sample, revisionOf(sample))
        }
        val result = database.inTransaction { catalog.snapshot(selected.slug) }
        assertThat(result.assets).isEmpty()
        assertThat(result.records["assets"]).isEmpty()
        assertThat(
            result.records["documents"]
                ?.single()
                ?.get("body")
                .toString(),
        ).contains(external.id.value.toString())
    }

    @Test
    fun `workspace and space exports retain owner saved local path links`() {
        val path = "/owner-saved/workspace"
        val profile = SpaceProfile.of("docs", "", listOf(ExternalLink.of(ExternalLinkKind.LOCAL_PATH, path)))
        val saved = database.inTransaction { ExposedSpaceRepository().add(space("docs").describe(profile)) }

        val workspace = database.inTransaction { catalog.snapshot(null) }
        val scoped = database.inTransaction { catalog.snapshot(saved.slug) }
        val expected =
            listOf(
                mapOf(
                    "space_id" to saved.id.value.toString(),
                    "link_position" to 0,
                    "kind" to "LOCAL_PATH",
                    "link_value" to path,
                ),
            )
        assertThat(workspace.records["spaceLinks"]).isEqualTo(expected)
        assertThat(scoped.records["spaceLinks"]).isEqualTo(expected)
    }

    @Test
    fun `space export keeps only its document bindings and resolves old asset ids to stored originals`() {
        val source = database.inTransaction { ExposedSpaceRepository().add(space("source")) }
        val destination = database.inTransaction { ExposedSpaceRepository().add(space("destination")) }
        val originalAsset = asset(source.id, byteArrayOf(1))
        val destinationAsset = asset(destination.id, byteArrayOf(2))
        val moved = document(destination.id, "moved", listOf(paragraph("imageblock", "[file](asset:${originalAsset.id.value})")))
        val outside = document(source.id, "outside", emptyList())
        database.inTransaction {
            val assets = ExposedAssetRepository()
            val documents = ExposedDocumentRepository()
            documents.add(moved, revisionOf(moved))
            documents.add(outside, revisionOf(outside))
            assets.add(originalAsset)
            assets.add(destinationAsset)
            binding(moved.id.value, originalAsset.id.value, destinationAsset.id.value)
            binding(outside.id.value, originalAsset.id.value, originalAsset.id.value)
        }

        val snapshot = database.inTransaction { catalog.snapshot(destination.slug) }
        assertThat(snapshot.records["documentAssetBindings"]).isEqualTo(
            listOf(
                mapOf(
                    "document_id" to moved.id.value.toString(),
                    "referenced_asset_id" to originalAsset.id.value.toString(),
                    "stored_asset_id" to destinationAsset.id.value.toString(),
                ),
            ),
        )
        assertThat(snapshot.assets.map { it.id }).containsExactly(destinationAsset.id.value.toString())
        assertThat(snapshot.assets.single().sha256).isEqualTo(destinationAsset.content.sha256)
        assertThat(
            snapshot.records["documents"]
                ?.single()
                ?.get("body")
                .toString(),
        ).contains(originalAsset.id.value.toString())
        assertThat(
            snapshot.records["revisions"]
                ?.single()
                ?.get("body")
                .toString(),
        ).contains(originalAsset.id.value.toString())
        assertThat(snapshot.markdown[moved.id.value.toString()]).contains("asset:${originalAsset.id.value}")
    }

    @Test
    fun `destination export is independent of the original asset and space rows`() {
        val destination = database.inTransaction { ExposedSpaceRepository().add(space("standalone")) }
        val logicalReference = UUID.randomUUID()
        val stored = asset(destination.id, byteArrayOf(3, 4))
        val moved = document(destination.id, "moved", listOf(paragraph("boundimage", "[file](asset:$logicalReference)")))
        database.inTransaction {
            ExposedAssetRepository().add(stored)
            ExposedDocumentRepository().add(moved, revisionOf(moved))
            binding(moved.id.value, logicalReference, stored.id.value)
        }

        val snapshot = database.inTransaction { catalog.snapshot(destination.slug) }
        assertThat(snapshot.records["spaces"]?.map { it["id"] }).containsExactly(destination.id.value.toString())
        assertThat(snapshot.records["assets"]?.map { it["id"] }).containsExactly(stored.id.value.toString())
        assertThat(snapshot.records["documentAssetBindings"]).isEqualTo(
            listOf(
                mapOf(
                    "document_id" to moved.id.value.toString(),
                    "referenced_asset_id" to logicalReference.toString(),
                    "stored_asset_id" to stored.id.value.toString(),
                ),
            ),
        )
        assertThat(snapshot.assets.single().sha256).isEqualTo(stored.content.sha256)
        assertThat(
            snapshot.records["revisions"]
                ?.single()
                ?.get("body")
                .toString(),
        ).contains(logicalReference.toString())
    }

    private fun asset(
        space: SpaceId,
        bytes: ByteArray,
    ): Asset =
        Asset.upload(
            AssetId(UUID.randomUUID()),
            space,
            AssetContent.of(AssetMediaType.PNG, bytes),
            AssetOrigin("image.png", null, person, at),
        )

    private fun binding(
        document: UUID,
        referenced: UUID,
        stored: UUID,
    ) {
        DocumentAssetBindingsTable.insert {
            it[documentId] = document
            it[referencedAssetId] = referenced
            it[storedAssetId] = stored
        }
    }

    @Test
    fun `export retains trash archived spaces and unconfirmed edits without changing their versions`() {
        val archived = database.inTransaction { ExposedSpaceRepository().add(space("archive").archive(at)) }
        val original = document(archived.id, "pending", listOf(paragraph("firstblock", "before")))
        val pending = original.amend(original.header, listOf(paragraph("firstblock", "after")), at.plusSeconds(1))
        val trashed = requireNotNull(document(archived.id, "trash", emptyList()).trash(at))
        database.inTransaction {
            val repository = ExposedDocumentRepository()
            repository.add(original, revisionOf(original))
            repository.savePending(pending, revisionOf(pending).copy(number = original.revision + 1))
            repository.add(trashed, revisionOf(trashed))
        }
        val first = database.inTransaction { catalog.snapshot(null) }
        val second = database.inTransaction { catalog.snapshot(archived.slug) }
        assertThat(first.records["documents"]).hasSize(2)
        assertThat(first.records["spaces"]?.single()?.get("archived_at")).isNotNull()
        assertThat(first.records["revisions"]?.count { it["pending"] == true }).isEqualTo(1)
        assertThat(first.records["documents"]?.first { it["id"] == original.id.value.toString() }?.get("content_version"))
            .isEqualTo(pending.version)
        assertThat(second.records).isEqualTo(first.records)
        assertThat(first.records.keys).doesNotContain("tokens", "sessions", "connections", "webhooks")
    }
}
