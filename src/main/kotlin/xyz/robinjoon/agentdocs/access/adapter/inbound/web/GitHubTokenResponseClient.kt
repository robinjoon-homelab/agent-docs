package xyz.robinjoon.agentdocs.access.adapter.inbound.web

import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.http.converter.FormHttpMessageConverter
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter
import org.springframework.web.client.RestClient

// Spring Security's default RestClient picks Apache HttpClient because it is on the classpath, and since 2026-10-24
// GitHub's token response has a line longer than Apache allows (MessageConstraintException), so every sign-in failed
// after GitHub had issued the token. The JDK client reads it. Converters and error handling match Spring's defaults.
object GitHubTokenResponseClient {
    fun create(): OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> =
        RestClientAuthorizationCodeTokenResponseClient().apply {
            setRestClient(
                RestClient
                    .builder()
                    .requestFactory(JdkClientHttpRequestFactory())
                    .messageConverters(listOf(FormHttpMessageConverter(), OAuth2AccessTokenResponseHttpMessageConverter()))
                    .defaultStatusHandler(OAuth2ErrorResponseErrorHandler())
                    .build(),
            )
        }
}
