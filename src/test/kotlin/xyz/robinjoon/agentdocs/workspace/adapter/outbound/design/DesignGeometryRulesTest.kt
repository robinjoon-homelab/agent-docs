package xyz.robinjoon.agentdocs.workspace.adapter.outbound.design

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.agentdocs.workspace.domain.document.InvalidDocumentException

class DesignGeometryRulesTest {
    private val mapper = JsonMapper.builder().build()

    @ParameterizedTest
    @ValueSource(
        strings = [
            """{"kind":"shape","geometry":{"kind":"arc","closure":"chord","innerRatio":0.5}}""",
            """{"kind":"shape","geometry":{"kind":"line"},"style":{"strokes":[{"align":"inside"}]}}""",
            """{"kind":"vector","contours":[{"id":"a","closed":false,"vertices":[{"id":"p","in":{"x":1,"y":0}}]}]}""",
            """{"kind":"vector","contours":[{"id":"a","closed":true,"vertices":[{"id":"p","arcToNext":{}},{"id":"q","in":{"x":1,"y":2}}]}]}""",
            """{"kind":"vector","contours":[{"id":"a","closed":true,"vertices":[{"id":"p"},{"id":"p"}]}]}""",
            """{"kind":"mask","mode":"outline","source":{"kind":"group","children":[{"kind":"text"}]}}""",
            """{"kind":"boolean","operands":[{"kind":"group","children":[{"kind":"image"}]}]}""",
        ],
    )
    fun `rejects inconsistent reference geometry`(geometry: String) {
        assertThatThrownBy { DesignGeometryRules.validate(mapper.readTree(geometry)) }.isInstanceOf(InvalidDocumentException::class.java)
    }

    @Test
    fun `accepts an open network and verifies directed boundaries when a region exists`() {
        val loop =
            """{"id":"triangle","fillRule":"nonzero","loops":[[{"segment":"ab"},{"segment":"cb","reverse":true},{"segment":"ca"}]]}"""
        val net =
            """{"vertices":[{"id":"a"},{"id":"b"},{"id":"c"}],
              "segments":[{"id":"ab","from":"a","to":"b"},{"id":"cb","from":"c","to":"b"},{"id":"ca","from":"c","to":"a"}],
              "regions":[$loop]}"""
        DesignGeometryNetwork.validate(mapper.readTree(net))
        DesignGeometryNetwork.validate(mapper.readTree(net.replace(loop, "")))
        assertThatThrownBy { DesignGeometryNetwork.validate(mapper.readTree(net.replace("true", "false"))) }.hasMessageContaining("closed")
        assertThatThrownBy { DesignGeometryNetwork.validate(mapper.readTree(net.replace(""""from":"c"""", """"from":"unknown""""))) }
            .hasMessageContaining("endpoint")
    }

    @Test
    fun `gradient stop order and paint identities remain stable`() {
        val stops = """[{"id":"s","at":0.3,"color":"#000"},{"id":"e","at":1,"color":"#fff"}]"""
        val surface = """{"fills":[{"kind":"linear","id":"fill","stops":$stops}]}"""
        val tokens = DesignTokenRules(null)
        tokens.validateVisual(mapper.readTree(surface))
        assertThatThrownBy { tokens.validateVisual(mapper.readTree(surface.replace(""""at":1""", """"at":0.2"""))) }
            .hasMessageContaining("ascending")
        assertThatThrownBy { tokens.validateVisual(mapper.readTree(surface.replace(""""id":"e"""", """"id":"s""""))) }
            .hasMessageContaining("duplicate id")
    }
}
