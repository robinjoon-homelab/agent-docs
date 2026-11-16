package xyz.robinjoon.agentdocs.workspace.application.model

class ExportAssetMissingException(
    val asset: ExportAsset,
    cause: AssetContentMissingException,
) : RuntimeException("export attachment original is missing", cause)
