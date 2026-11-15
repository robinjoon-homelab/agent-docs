package xyz.robinjoon.agentdocs.workspace.domain.editsession

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures
import java.util.UUID

class HumanInputBatchTest {
    private val at = WorkspaceFixtures.now

    @Test
    fun `multiple snapshots from one foreground join without merging another foreground`() {
        val tabA = UUID.randomUUID()
        val tabB = UUID.randomUUID()
        val first = InputTrace(tabA, UUID.randomUUID(), at, at)
        val second = InputTrace(tabA, UUID.randomUUID(), at.plusSeconds(60), at.plusSeconds(60))
        val third = InputTrace(tabB, UUID.randomUUID(), at.plusSeconds(360), at.plusSeconds(360))
        val batch = HumanInputBatch(UUID.randomUUID(), listOf(third, first, second))
        val groups = batch.foregrounds()
        assertThat(groups).hasSize(2)
        assertThat(groups.single { it.tabId == tabA }.intervals).containsExactly(InputInterval(at, at.plusSeconds(60)))
        assertThat(groups.single { it.tabId == tabB }.intervals).containsExactly(InputInterval(at.plusSeconds(360), at.plusSeconds(360)))
        assertThat(batch.traces.map { it.batchId }).containsExactly(third.batchId, first.batchId, second.batchId)
    }

    @Test
    fun `legacy single input keeps its batch identity and gets a defensive child list`() {
        val trace = InputTrace(UUID.randomUUID(), UUID.randomUUID(), at, at)
        val children = mutableListOf(trace)
        val batch = HumanInputBatch(trace.batchId, children)
        children.clear()
        assertThat(batch.traces).containsExactly(trace)
        assertThat(HumanInputBatch.single(trace).batchId).isEqualTo(trace.batchId)
    }

    @Test
    fun `every foreground is validated and an empty batch is rejected`() {
        val valid = InputTrace(UUID.randomUUID(), UUID.randomUUID(), at, at)
        val future = InputTrace(UUID.randomUUID(), UUID.randomUUID(), at.plusSeconds(1), at.plusSeconds(1))
        val batch = HumanInputBatch(UUID.randomUUID(), listOf(valid, future))
        assertThatThrownBy { batch.requireValidAt(at) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { HumanInputBatch(UUID.randomUUID(), emptyList()) }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
