package com.galgotias.docscanner.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration: CORS policy and static resource serving.
 *
 * NOTE: We deliberately do NOT override addResourceHandlers for "/**" to avoid
 * conflicting with Spring Boot's WelcomePageHandlerMapping (index.html).
 * Spring Boot auto-handles classpath:/static/ perfectly.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    // Static resources are served automatically by Spring Boot from:
    // - classpath:/static/   (our index.html, css/, js/)
    // - We do NOT override addResourceHandlers to avoid breaking welcome page mapping
}
