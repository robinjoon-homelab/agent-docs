package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.v1.jdbc.insert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.support.persistence.MigratedH2Database
import xyz.robinjoon.agentdocs.workspace.domain.asset.Asset
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetContent
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetMediaType
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetOrigin
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.DrawingBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.InvalidDocumentException
import xyz.robinjoon.agentdocs.workspace.domain.space.Space
import java.util.UUID

// The document repository's write guard: a drawing may only point at an uploaded SVG and scene of its own space. Only
// the repository's stored result is checked here; which ids count as references is ExposedDocumentAssetStoreTest's.
class DrawingAssetGuardTest {
    private val database = MigratedH2Database()
    private val fixture = TransferPersistenceFixtures(database)
    private val documents = ExposedDocumentRepository()

    @AfterEach
    fun closeDatabase() = database.close()

    private fun uploaded(
        space: Space,
        type: AssetMediaType,
    ): Asset {
        val asset =
            Asset.upload(
                AssetId(UUID.randomUUID()),
                space.id,
                AssetContent.of(type, "${type.name}-${UUID.randomUUID()}".toByteArray()),
                AssetOrigin("draw.${type.extension}", null, PersistenceFixtures.person, PersistenceFixtures.at),
            )
        return database.inTransaction { ExposedAssetRepository().add(asset) }
    }

    private fun add(
        space: Space,
        drawing: DrawingBlock,
    ) {
        val document = PersistenceFixtures.document(space.id, "drawing-${UUID.randomUUID()}", listOf(drawing))
        database.inTransaction { documents.add(document, PersistenceFixtures.revisionOf(document)) }
    }

    @Test
    fun `a drawing of an uploaded SVG and scene is stored and reads back as the same drawing`() {
        val svg = uploaded(fixture.source, AssetMediaType.SVG)
        val scene = uploaded(fixture.source, AssetMediaType.EXCALIDRAW)
        val drawing = DrawingBlock(BlockId("draw000001"), svg.id, scene.id, "흐름")
        val document = PersistenceFixtures.document(fixture.source.id, "flow", listOf(drawing))

        val stored = database.inTransaction { documents.add(document, PersistenceFixtures.revisionOf(document)) }

        assertThat(stored.blocks).containsExactly(drawing)
    }

    @Test
    fun `a drawing is refused when a role holds the wrong type, an asset was never uploaded, or it sits in another space`() {
        val svg = uploaded(fixture.source, AssetMediaType.SVG)
        val scene = uploaded(fixture.source, AssetMediaType.EXCALIDRAW)
        val png = uploaded(fixture.source, AssetMediaType.PNG)
        val anotherSvg = uploaded(fixture.source, AssetMediaType.SVG)
        val elsewhere = uploaded(fixture.target, AssetMediaType.EXCALIDRAW)
        val missing = AssetId(UUID.randomUUID())

        listOf(
            DrawingBlock(BlockId("draw000002"), png.id, scene.id, ""),
            DrawingBlock(BlockId("draw000003"), svg.id, anotherSvg.id, ""),
            DrawingBlock(BlockId("draw000004"), svg.id, missing, ""),
            DrawingBlock(BlockId("draw000005"), svg.id, elsewhere.id, ""),
        ).forEach { drawing ->
            assertThatThrownBy { add(fixture.source, drawing) }.isInstanceOf(InvalidDocumentException::class.java)
        }
    }

    @Test
    fun `after a transfer a new revision of the drawing is stored through bindings to copies of the right type`() {
        val svg = uploaded(fixture.source, AssetMediaType.SVG)
        val scene = uploaded(fixture.source, AssetMediaType.EXCALIDRAW)
        val svgCopy = uploaded(fixture.target, AssetMediaType.SVG)
        val sceneCopy = uploaded(fixture.target, AssetMediaType.EXCALIDRAW)
        val moved = fixture.document(fixture.target, "moved", "![흐름](asset:${svg.id} \"excalidraw asset:${scene.id}\")")
        database.inTransaction {
            listOf(svg.id to svgCopy.id, scene.id to sceneCopy.id).forEach { (referenced, copy) ->
                DocumentAssetBindingsTable.insert {
                    it[documentId] = moved.id.value
                    it[referencedAssetId] = referenced.value
                    it[storedAssetId] = copy.value
                }
            }
        }
        val edited =
            moved.revise(moved.header, moved.blocks + PersistenceFixtures.paragraph("para01", "메모"), PersistenceFixtures.at.plusSeconds(5))

        val updated = database.inTransaction { documents.update(edited, PersistenceFixtures.revisionOf(edited), amended = false) }

        assertThat(updated.blocks).containsExactlyElementsOf(edited.blocks)
    }
}
