package xyz.robinjoon.agentdocs.workspace.application.model

// The image address could not be downloaded: the host did not resolve, the connection failed, or it timed out.
class ImageDownloadException(
    url: String,
    cause: Throwable,
) : InvalidRequestException("could not download an image from $url", "download it and upload the file", cause)
