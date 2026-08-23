package io.octatec.horext.api.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    @Value("\${app.cors.allowed-origins}") allowedOrigins: String,
) : WebMvcConfigurer {
    private val allowedOrigins =
        allowedOrigins
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toTypedArray()

    override fun addCorsMappings(registry: CorsRegistry) {
        registry
            .addMapping("/**")
            .allowedOrigins(*allowedOrigins)
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
    }
}
