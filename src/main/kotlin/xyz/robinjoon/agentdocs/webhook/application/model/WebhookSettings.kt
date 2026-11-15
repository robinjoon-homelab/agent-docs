package xyz.robinjoon.agentdocs.webhook.application.model

// publicUrl is the address people open, so links in payloads lead to the document in the web app.
data class WebhookSettings(
    val publicUrl: String,
)
