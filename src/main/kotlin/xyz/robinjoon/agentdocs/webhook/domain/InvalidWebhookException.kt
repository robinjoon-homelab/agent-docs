package xyz.robinjoon.agentdocs.webhook.domain

class InvalidWebhookException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)
