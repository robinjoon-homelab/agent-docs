package xyz.robinjoon.agentdocs.config

import org.springframework.context.annotation.Configuration
import org.springframework.http.CacheControl
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
}
