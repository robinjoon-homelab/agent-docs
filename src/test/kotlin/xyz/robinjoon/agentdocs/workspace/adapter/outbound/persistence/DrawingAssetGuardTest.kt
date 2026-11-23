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
import xyz.robinjoon.agentdocs.workspace.domain.document.Document
import xyz.robinjoon.agentdocs.workspace.domain.document.DrawingBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowDesignBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.InvalidDocumentException
import xyz.robinjoon.agentdocs.workspace.domain.document.history.RevisionKind
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
    fun `design icons must be uploaded SVGs within the document space`() {
        val svg = uploaded(fixture.source, AssetMediaType.SVG)
        val png = uploaded(fixture.source, AssetMediaType.PNG)
        val foreign = uploaded(fixture.target, AssetMediaType.SVG)
        val saved = saveDesign(svg.id)
        assertThat(saved.blocks).hasSize(2)
        val retained = database.inTransaction { ExposedDocumentAssetStore().references(saved.id) }
        assertThat(retained).containsExactly(svg.id)
        for (id in listOf(png.id, foreign.id, AssetId(UUID.randomUUID()))) {
            assertThatThrownBy { saveDesign(id) }.isInstanceOf(InvalidDocumentException::class.java)
        }
    }

    private fun saveDesign(icon: AssetId): Document {
        val draft = PersistenceFixtures.document(fixture.source.id, "icons-$icon", emptyList())
        val settings = FlowDesignBlock.parse(BlockId("settings"), """{"kind":"settings","id":"defaults","modelVersion":4}""")
        val screen =
            FlowDesignBlock.parse(
                BlockId("screen01"),
                """{"kind":"screen","id":"home","code":"F-01",
            "root":{"kind":"icon","id":"search","icon":{"svgAsset":"$icon"}}}""",
            )
        val doc = Document.create(draft.id, draft.header.copy(type = "flow"), listOf(settings, screen), PersistenceFixtures.at)
        return database.inTransaction { documents.add(doc, PersistenceFixtures.revisionOf(doc)) }
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

    @Test
    fun `normal edits must replace SVG and scene together on the same block id while alt-only and restores remain valid`() {
        val svg1 = uploaded(fixture.source, AssetMediaType.SVG)
        val scene1 = uploaded(fixture.source, AssetMediaType.EXCALIDRAW)
        val svg2 = uploaded(fixture.source, AssetMediaType.SVG)
        val scene2 = uploaded(fixture.source, AssetMediaType.EXCALIDRAW)
        val initial = DrawingBlock(BlockId("draw000001"), svg1.id, scene1.id, "초안")
        val doc = PersistenceFixtures.document(fixture.source.id, "pair-edit", listOf(initial))
        val created = database.inTransaction { documents.add(doc, PersistenceFixtures.revisionOf(doc)) }

        val svgOnly = created.revise(created.header, listOf(initial.copy(svg = svg2.id)), PersistenceFixtures.at.plusSeconds(2))
        val sceneOnly = created.revise(created.header, listOf(initial.copy(scene = scene2.id)), PersistenceFixtures.at.plusSeconds(3))
        assertThatThrownBy {
            database.inTransaction { documents.update(svgOnly, PersistenceFixtures.revisionOf(svgOnly), amended = false) }
        }.isInstanceOf(InvalidDocumentException::class.java)
        assertThatThrownBy {
            database.inTransaction { documents.savePending(sceneOnly, PersistenceFixtures.revisionOf(sceneOnly)) }
        }.isInstanceOf(InvalidDocumentException::class.java)

        val altOnly = created.amend(created.header, listOf(initial.copy(alt = "설명 추가")), PersistenceFixtures.at.plusSeconds(4))
        val afterAlt = database.inTransaction { documents.savePending(altOnly, PersistenceFixtures.revisionOf(altOnly).copy(number = 2)) }
        assertThat(afterAlt.blocks).containsExactly(initial.copy(alt = "설명 추가"))

        val confirmed = database.inTransaction { documents.confirmPending(afterAlt) }
        val both =
            confirmed.revise(
                confirmed.header,
                listOf(DrawingBlock(BlockId("draw000001"), svg2.id, scene2.id, "수정됨")),
                PersistenceFixtures.at.plusSeconds(5),
            )
        val afterBoth = database.inTransaction { documents.update(both, PersistenceFixtures.revisionOf(both), amended = false) }
        assertThat(afterBoth.blocks).containsExactly(DrawingBlock(BlockId("draw000001"), svg2.id, scene2.id, "수정됨"))

        val restored = afterBoth.revise(afterBoth.header, listOf(initial.copy(svg = svg1.id)), PersistenceFixtures.at.plusSeconds(6))
        val restoreRev = PersistenceFixtures.revisionOf(restored).let { it.copy(change = it.change.withKind(RevisionKind.RESTORE)) }
        val afterRestore = database.inTransaction { documents.update(restored, restoreRev, amended = false) }
        assertThat(afterRestore.blocks).containsExactly(initial.copy(svg = svg1.id))
    }
}
