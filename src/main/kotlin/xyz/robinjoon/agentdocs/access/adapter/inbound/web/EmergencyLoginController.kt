package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import xyz.robinjoon.agentdocs.access.application.model.AccessSettings
import xyz.robinjoon.agentdocs.access.application.port.input.EmergencyLoginUseCase

@RestController
class EmergencyLoginController(
    private val emergency: EmergencyLoginUseCase,
    settings: AccessSettings,
) {
    private val cookie = SessionCookie(settings.issuer.startsWith("https://"))

    @GetMapping("/emergency-login", produces = ["text/html;charset=UTF-8"])
    fun confirm(): ResponseEntity<String> =
        ResponseEntity
            .ok()
            .header("Cache-Control", "no-store")
            .header("Referrer-Policy", "no-referrer")
            .header("Content-Security-Policy", "default-src 'none'; script-src 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'")
            .body(page)

    @PostMapping("/emergency-login")
    fun consume(
        @RequestParam token: String,
        response: HttpServletResponse,
    ): ResponseEntity<Void> {
        response.setHeader("Cache-Control", "no-store")
        return try {
            cookie.write(response, emergency.consume(token))
            ResponseEntity.noContent().build()
        } catch (unavailable: IllegalArgumentException) {
            logger.debug("Emergency login unavailable ({})", unavailable.javaClass.simpleName)
            ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(EmergencyLoginController::class.java)
        val page =
            """
            <!doctype html><html lang="ko"><meta charset="utf-8"><title>비상 로그인</title>
            <h1>비상 로그인</h1><p>현재 소유자로 로그인합니다. 링크는 10분 안에 한 번만 사용할 수 있습니다.</p>
            <button id="confirm" type="button">로그인</button><p id="result" role="status"></p>
            <script>
            const token = location.hash.slice(1);
            history.replaceState(null, '', '/emergency-login');
            document.getElementById('confirm').onclick = async function() {
              this.disabled = true;
              const csrf = document.cookie.split('; ').find(c => c.startsWith('XSRF-TOKEN='));
              if (!csrf) { document.getElementById('result').textContent = '새 링크를 열어 다시 시도하세요.'; return; }
              try {
                const response = await fetch('/emergency-login', {
                  method: 'POST', credentials: 'same-origin',
                  headers: {'X-XSRF-TOKEN': decodeURIComponent(csrf.slice(11)), 'Content-Type': 'application/x-www-form-urlencoded'},
                  body: new URLSearchParams({token})
                });
                if (response.ok) { location.replace('/'); return; }
              } catch (error) { /* A failed request must never resubmit a one-use credential automatically. */ }
              document.getElementById('result').textContent = '로그인할 수 없습니다. 서버에서 새 링크를 발급하세요.';
            };
            </script></html>
            """.trimIndent()
    }
}
