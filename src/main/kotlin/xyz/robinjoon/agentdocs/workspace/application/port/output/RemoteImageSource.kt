package xyz.robinjoon.agentdocs.workspace.application.port.output

import xyz.robinjoon.agentdocs.workspace.application.model.RemoteImage

interface RemoteImageSource {
    // Downloads a public http(s) image of at most the asset size limit. Returns null when the address is not public,
    // the answer is not 200, or the body is empty or too large; fails with ImageDownloadException when the host does
    // not resolve or the connection fails.
    fun fetch(url: String): RemoteImage?
}
