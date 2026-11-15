package xyz.robinjoon.agentdocs.webhook.application.model

// What the owner sends to add an endpoint: its address and the event types it takes, by their wire names.
data class EndpointRegistration(
    val url: String,
    val events: Set<String>,
)
