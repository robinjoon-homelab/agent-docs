package xyz.robinjoon.agentdocs.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Configuration
import org.springframework.http.CacheControl
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.time.Duration

// Vite names every file under assets/ by its content hash, so a browser may keep them for a year. Other static files
// keep their name across releases and are revalidated instead (spring.web.resources.cache in application.yaml).
@Configuration(proxyBeanMethods = false)
class WebAppConfig : WebMvcConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry
            .addResourceHandler("/assets/**")
            .addResourceLocations("classpath:/static/assets/")
            .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
    }

    // Every screen is index.html, reached directly or by forward, so this one path carries the page policy. It only
    // keeps fonts and connections on this origin: the drawing editor would otherwise fall back to a font CDN and fetch
    // image addresses pasted as HTML. Scripts, images, styles, and frames keep their current behavior.
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(PagePolicy).addPathPatterns("/index.html")
    }

    private object PagePolicy : HandlerInterceptor {
        override fun preHandle(
            request: HttpServletRequest,
            response: HttpServletResponse,
            handler: Any,
        ): Boolean {
            response.setHeader("Content-Security-Policy", "font-src 'self' data:; connect-src 'self'")
            return true
        }
    }
}
