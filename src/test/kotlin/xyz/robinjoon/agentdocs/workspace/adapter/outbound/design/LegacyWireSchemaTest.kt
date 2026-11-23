package xyz.robinjoon.agentdocs.workspace.adapter.outbound.design

import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.agentdocs.workspace.domain.document.BlockId
import xyz.robinjoon.agentdocs.workspace.domain.document.DocumentId
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowAppearance
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowBehavior
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowControls
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowDescription
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowDesignBlock
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowElement
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowPlacement
import xyz.robinjoon.agentdocs.workspace.domain.document.FlowRules
import xyz.robinjoon.agentdocs.workspace.domain.document.LegacyFlowUpgrade
import java.util.UUID

class LegacyWireSchemaTest {
    @Test
    fun `every migrated primitive and its editable component satisfy the design write contract`() {
        val asset = "ddd5d9a3-e818-43af-9be9-228de79ea8f1"
        val elements =
            FlowAppearance.kindNames().map { kind ->
                val items = if (kind == "list") listOf("first", "second") else null
                val date = if (kind == "path") "2026-11-24" else null
                FlowElement(
                    BlockId("test$kind"),
                    FlowAppearance.of(kind, FlowAppearance.defaultLook(kind), items, date),
                    FlowPlacement(10, 5, 128, 44),
                    FlowDescription("", if (kind == "image") "asset:$asset" else "값", ""),
                    FlowBehavior.of(FlowControls(false, null, true), emptyList(), emptyList()),
                )
            }
        val blocks = LegacyFlowUpgrade.convert(FlowRules.initial("desktop", "") + elements)
        val document = FlowDesignBlock.documentJson(DocumentId(UUID.randomUUID()), blocks)
        val validator = JsonFlowDesignValidator(JsonMapper.builder().build())

        assertThatCode { validator.validate(document) }.doesNotThrowAnyException()
    }
}
