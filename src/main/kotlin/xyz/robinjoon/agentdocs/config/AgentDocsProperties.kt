package xyz.robinjoon.agentdocs.config

import org.springframework.boot.context.properties.ConfigurationProperties

// Instance settings under agent-docs.*. publicUrl is the address people and MCP clients use, such as
// https://docs.example.com; it is also the OAuth issuer.
@ConfigurationProperties("agent-docs")
data class AgentDocsProperties(
    val publicUrl: String,
    val owner: String,
    val members: List<String>,
    val assets: AssetProperties,
)

data class AssetProperties(
    val store: String,
    val directory: String,
    val s3: S3Properties,
)

data class S3Properties(
    val bucket: String,
    val prefix: String,
    val endpoint: String,
    val region: String,
)
