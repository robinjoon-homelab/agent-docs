package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.support.persistence.MigratedH2Database
import xyz.robinjoon.agentdocs.workspace.application.model.DocumentAssetBinding
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowAppearance
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowBehavior
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowControls
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowDescription
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowElement
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowPlacement
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowRules
import xyz.robinjoon.agentdocs.workspace.domain.document.markdown.BlockMarkdown

class ExposedDocumentAssetStoreTest {
    private val database = MigratedH2Database()
    private val fixture = TransferPersistenceFixtures(database)
    private val store = ExposedDocumentAssetStore()

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `references include historical body and comment edits but exclude escaped and code examples`() {
        val historical = fixture.asset(fixture.source)
        val comment = fixture.asset(fixture.source)
        val edited = fixture.asset(fixture.source)
        val literal = fixture.asset(fixture.source)
        val doc = fixture.document(fixture.source, "one", "![old](asset:${historical.id})")
        database.inTransaction { DocumentsTable.update({ DocumentsTable.id eq doc.id.value }) { it[body] = "No current attachment" } }
        fixture.comment(
            doc,
            "[old](asset:${comment.id})",
            "[new](asset:${edited.id}) `[code](asset:${literal.id})` \\[escaped](asset:${literal.id})",
        )
        assertThat(database.inTransaction { store.references(doc.id) }).containsExactlyInAnyOrder(historical.id, comment.id, edited.id)
    }

    @Test
    fun `a drawing's SVG and scene both count as references, in the current body and in history`() {
        val svg = fixture.asset(fixture.source)
        val scene = fixture.asset(fixture.source)
        val oldSvg = fixture.asset(fixture.source)
        val oldScene = fixture.asset(fixture.source)
        val current = fixture.document(fixture.source, "current", "![흐름](asset:${svg.id} \"excalidraw asset:${scene.id}\")")
        val past = fixture.document(fixture.source, "past", "![흐름](asset:${oldSvg.id} \"excalidraw asset:${oldScene.id}\")")
        database.inTransaction { DocumentsTable.update({ DocumentsTable.id eq past.id.value }) { it[body] = "No current attachment" } }

        assertThat(database.inTransaction { store.references(current.id) }).containsExactlyInAnyOrder(svg.id, scene.id)
        assertThat(database.inTransaction { store.references(past.id) }).containsExactlyInAnyOrder(oldSvg.id, oldScene.id)
    }

    @Test
    fun `flow image elements count as references of their flow document`() {
        val image = fixture.asset(fixture.source)
        val doc = fixture.document(fixture.source, "flow", "No current attachment")
        val element =
            FlowElement(
                BlockId("image1"),
                FlowAppearance.of("image", null, null, null),
                FlowPlacement(0, 0, 200, 100),
                FlowDescription("화면 캡처", "asset:${image.id}", ""),
                FlowBehavior.of(FlowControls(false, null, false), emptyList(), emptyList()),
            )
        val body = BlockMarkdown.write(FlowRules.initial("phone", "M") + element, annotated = true)
        database.inTransaction {
            DocumentsTable.update({ DocumentsTable.id eq doc.id.value }) {
                it[DocumentsTable.body] = body
                it[DocumentsTable.type] = "flow"
            }
        }

        assertThat(database.inTransaction { store.references(doc.id) }).containsExactly(image.id)
    }

    @Test
    fun `sharing resolves actual bindings and keeps even retained bindings conservative`() {
        val original = fixture.asset(fixture.source)
        val copy = fixture.asset(fixture.target)
        val moving = fixture.document(fixture.target, "moving", "[file](asset:${original.id})")
        val staying = fixture.document(fixture.target, "staying", "No current attachment")
        database.inTransaction {
            store.bind(
                listOf(
                    DocumentAssetBinding(
                        moving.id,
                        original.id,
                        copy.id,
                    ),
                    DocumentAssetBinding(
                        staying.id,
                        original.id,
                        copy.id,
                    ),
                ),
            )
        }
        assertThat(database.inTransaction { store.sharedOutside(setOf(copy.id), setOf(moving.id)) }).containsExactly(copy.id)
        assertThat(database.inTransaction { store.sharedOutside(setOf(original.id), setOf(moving.id)) }).isEmpty()
        assertThat(database.inTransaction { store.sharedOutside(setOf(copy.id), setOf(moving.id, staying.id)) }).isEmpty()
    }

    @Test
    fun `binding replacement preserves logical identity and document purge cascades its bindings`() {
        val original = fixture.asset(fixture.source)
        val first = fixture.asset(fixture.target)
        val second = fixture.asset(fixture.target)
        val doc = fixture.document(fixture.target, "one", "[file](asset:${original.id})", false)
        database.inTransaction { store.bind(listOf(DocumentAssetBinding(doc.id, original.id, first.id))) }
        database.inTransaction { store.bind(listOf(DocumentAssetBinding(doc.id, original.id, second.id))) }
        assertThat(database.inTransaction { store.bindings(setOf(doc.id)) }).containsExactly(
            DocumentAssetBinding(
                doc.id,
                original.id,
                second.id,
            ),
        )
        database.inTransaction { DocumentsTable.deleteWhere { id eq doc.id.value } }
        assertThat(database.inTransaction { store.bindings(setOf(doc.id)) }).isEmpty()
    }
}
