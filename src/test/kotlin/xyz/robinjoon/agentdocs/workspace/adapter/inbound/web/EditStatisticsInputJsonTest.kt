package xyz.robinjoon.agentdocs.workspace.adapter.inbound.web

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.agentdocs.workspace.application.WorkspaceFixtures
import java.util.UUID

class EditStatisticsInputJsonTest {
    private val json = JsonMapper.builder().build()
    private val at = WorkspaceFixtures.now

    @Test
    fun `request envelope retains the whole batch identity and the actual foreground of every child`() {
        val outer = UUID.randomUUID()
        val tabA = UUID.randomUUID()
        val tabB = UUID.randomUUID()
        val body = json.valueToTree<JsonNode>(mapOf("batchId" to outer, "traces" to listOf(child(tabA, 60), child(tabB, 360))))
        val batch = EditStatisticsJson.inputBatch(body)
        assertThat(batch.batchId).isEqualTo(outer)
        assertThat(batch.traces.map { it.tabId }).containsExactly(tabA, tabB)
        assertThat(batch.traces[0].firstInputAt).isEqualTo(at.plusSeconds(60))
        assertThat(batch.traces[1].firstInputAt).isEqualTo(at.plusSeconds(360))
    }

    @Test
    fun `legacy single trace is normalized to one child using its original batch ID`() {
        val tab = UUID.randomUUID()
        val legacy = child(tab, 0)
        val batch = EditStatisticsJson.inputBatch(json.valueToTree<JsonNode>(legacy))
        assertThat(batch.traces).hasSize(1)
        assertThat(batch.batchId.toString()).isEqualTo(legacy["batchId"])
        assertThat(batch.traces.single().tabId).isEqualTo(tab)
    }

    @Test
    fun `malformed or empty child arrays are rejected instead of falling back to legacy`() {
        val empty = json.valueToTree<JsonNode>(mapOf("batchId" to UUID.randomUUID(), "traces" to emptyList<Any>()))
        val wrong = json.valueToTree<JsonNode>(mapOf("batchId" to UUID.randomUUID(), "traces" to "invalid"))
        assertThatThrownBy { EditStatisticsJson.inputBatch(empty) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { EditStatisticsJson.inputBatch(wrong) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun child(
        tab: UUID,
        seconds: Long,
    ): Map<String, Any> {
        val timestamp = at.plusSeconds(seconds).toString()
        return mapOf(
            "tabId" to tab.toString(),
            "batchId" to UUID.randomUUID().toString(),
            "firstInputAt" to timestamp,
            "lastInputAt" to timestamp,
            "intervals" to listOf(mapOf("firstInputAt" to timestamp, "lastInputAt" to timestamp)),
        )
    }
}
