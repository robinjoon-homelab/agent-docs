package xyz.robinjoon.agentdocs.ordering.domain.order

class InvalidOrderException(
    message: String,
) : IllegalArgumentException(message)
