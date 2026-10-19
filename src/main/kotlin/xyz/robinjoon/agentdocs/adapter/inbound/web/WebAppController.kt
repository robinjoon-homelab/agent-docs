package xyz.robinjoon.agentdocs.adapter.inbound.web

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

// The web app routes in the browser, so its screens all load the same index.html. The approval screen shares
// /oauth/authorize with the OAuth endpoint; only the request without client_id is the screen.
@Controller
class WebAppController {
    @GetMapping("/login", "/inbox", "/search", "/settings/**", "/s/**", "/d/**")
    fun screen(): String = INDEX

    @GetMapping("/oauth/authorize", params = ["!client_id"])
    fun approval(): String = INDEX

    // The build places the generated API reference at docs/index.html; static resources have no directory index.
    @GetMapping("/docs")
    fun apiReference(): String = "redirect:/docs/index.html"

    private companion object {
        const val INDEX = "forward:/index.html"
    }
}
