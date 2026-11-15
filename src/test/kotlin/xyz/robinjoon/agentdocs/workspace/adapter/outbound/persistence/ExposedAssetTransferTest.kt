package xyz.robinjoon.agentdocs.workspace.adapter.outbound.persistence

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.support.persistence.MigratedH2Database
import xyz.robinjoon.agentdocs.workspace.domain.asset.AssetId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.InvalidDocumentException
import java.util.UUID

class ExposedAssetTransferTest {
    private val database = MigratedH2Database()
    private val fixture = TransferPersistenceFixtures(database)
    private val assets = ExposedAssetRepository()

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `exclusive ownership moves without changing bytes origin or asset identity`() {
        val original = fixture.asset(fixture.source)
        val moved = database.inTransaction { assets.update(original.moveTo(fixture.target.id)) }
        assertThat(moved).isEqualTo(original.moveTo(fixture.target.id))
        assertThat(database.inTransaction { assets.findById(original.id) }?.spaceId).isEqualTo(fixture.target.id)
    }

    @Test
    fun `a shared copy keeps source metadata and shares immutable bytes`() {
        val original = fixture.asset(fixture.source)
        val copy = database.inTransaction { assets.add(original.copyTo(AssetId(UUID.randomUUID()), fixture.target.id)) }
        assertThat(copy.id).isNotEqualTo(original.id)
        assertThat(copy.content).isEqualTo(original.content)
        assertThat(copy.origin).isEqualTo(original.origin)
        assertThat(database.inTransaction { assets.findById(original.id) }).isEqualTo(original)
    }

    @Test
    fun `a retained document cannot add a known asset after exclusive ownership has moved`() {
        val moved = fixture.asset(fixture.target)
        val doc =
            PersistenceFixtures.document(
                fixture.source.id,
                "new",
                listOf(
                    PersistenceFixtures.paragraph(
                        "block1",
                        "[file](asset:${moved.id})",
                    ),
                ),
            )
        assertThatThrownBy { database.inTransaction { ExposedDocumentRepository().add(doc, PersistenceFixtures.revisionOf(doc)) } }
            .isInstanceOf(InvalidDocumentException::class.java)
        assertThat(database.inTransaction { ExposedDocumentRepository().findById(doc.id) }).isNull()
    }
}
