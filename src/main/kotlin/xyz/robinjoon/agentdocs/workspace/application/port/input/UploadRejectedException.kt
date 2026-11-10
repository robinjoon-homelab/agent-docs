package xyz.robinjoon.agentdocs.workspace.application.port.input

class UploadRejectedException : RuntimeException("this upload token is unknown, has expired, or was already used")
