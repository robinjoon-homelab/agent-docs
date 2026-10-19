package xyz.robinjoon.agentdocs.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.intercept.AuthorizationFilter
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository
import org.springframework.security.web.csrf.CsrfFilter
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import org.springframework.security.web.util.matcher.RequestMatcher
import xyz.robinjoon.agentdocs.access.adapter.inbound.web.ApiAuthenticationEntryPoint
import xyz.robinjoon.agentdocs.access.adapter.inbound.web.BearerTokenFilter
import xyz.robinjoon.agentdocs.access.adapter.inbound.web.CsrfCookieFilter
import xyz.robinjoon.agentdocs.access.adapter.inbound.web.GitHubTokenDiscarder
import xyz.robinjoon.agentdocs.access.adapter.inbound.web.GitHubUserService
import xyz.robinjoon.agentdocs.access.adapter.inbound.web.NextPathAuthorizationRequestResolver
import xyz.robinjoon.agentdocs.access.adapter.inbound.web.RateLimitFilter
import xyz.robinjoon.agentdocs.access.adapter.inbound.web.SignInFailureHandler
import xyz.robinjoon.agentdocs.access.adapter.inbound.web.SignInSuccessHandler
import xyz.robinjoon.agentdocs.access.application.model.AccessSettings
import xyz.robinjoon.agentdocs.access.application.port.input.CallerUseCase
import xyz.robinjoon.agentdocs.access.application.port.input.SignInUseCase
import java.time.Clock

// Two chains. Agents and OAuth clients use stateless bearer tokens on /mcp and the OAuth endpoints. Everything else is
// the web app: GitHub sign-in with a session and CSRF tokens, plus bearer tokens for scripts on /api. Only the login
// page, the OAuth flow, health checks, and the static web app are open without signing in.
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class SecurityConfig {
    @Bean
    fun securityComponents(
        callers: CallerUseCase,
        settings: AccessSettings,
        clock: Clock,
    ): SecurityComponents =
        SecurityComponents(
            BearerTokenFilter(callers),
            RateLimitFilter(clock),
            ApiAuthenticationEntryPoint(settings),
        )

    @Bean
    @Order(1)
    fun agentSecurityFilterChain(
        http: HttpSecurity,
        components: SecurityComponents,
    ): SecurityFilterChain {
        http
            .securityMatcher("/mcp", "/oauth/token", "/oauth/register", "/.well-known/**")
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .securityContext { it.securityContextRepository(RequestAttributeSecurityContextRepository()) }
            .requestCache { it.disable() }
            .addFilterBefore(components.bearer, AuthorizationFilter::class.java)
            .addFilterAfter(components.rateLimit, BearerTokenFilter::class.java)
            .authorizeHttpRequests {
                it
                    .requestMatchers("/mcp")
                    .authenticated()
                    .anyRequest()
                    .permitAll()
            }.exceptionHandling { it.authenticationEntryPoint(components.entryPoint) }
        return http.build()
    }

    @Bean
    @Order(2)
    fun webSecurityFilterChain(
        http: HttpSecurity,
        components: SecurityComponents,
        signIn: SignInUseCase,
        registrations: ClientRegistrationRepository,
    ): SecurityFilterChain {
        val sessions = HttpSessionSecurityContextRepository()
        http
            .csrf { it.spa().ignoringRequestMatchers(bearerRequests) }
            .addFilterAfter(CsrfCookieFilter(), CsrfFilter::class.java)
            .addFilterBefore(components.bearer, AuthorizationFilter::class.java)
            .addFilterAfter(components.rateLimit, BearerTokenFilter::class.java)
            .authorizeHttpRequests {
                it
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll()
            }.exceptionHandling {
                it.defaultAuthenticationEntryPointFor(components.entryPoint, PathPatternRequestMatcher.withDefaults().matcher("/api/**"))
            }.oauth2Login { login ->
                login
                    .loginPage("/login")
                    .authorizationEndpoint { it.authorizationRequestResolver(NextPathAuthorizationRequestResolver(registrations)) }
                    .userInfoEndpoint { it.userService(GitHubUserService(signIn)) }
                    .authorizedClientRepository(GitHubTokenDiscarder())
                    .successHandler(SignInSuccessHandler(sessions))
                    .failureHandler(SignInFailureHandler())
            }.logout { it.logoutUrl("/logout").logoutSuccessUrl("/login") }
        return http.build()
    }

    private companion object {
        // A request that brings its own bearer token is not riding on a browser session, so CSRF does not apply.
        val bearerRequests =
            RequestMatcher { request ->
                request.getHeader("Authorization")?.startsWith("Bearer ", ignoreCase = true) ==
                    true
            }
    }
}
