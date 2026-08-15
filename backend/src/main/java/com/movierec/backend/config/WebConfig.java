package com.movierec.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration for the backend, currently used to set up CORS so the
 * local Vite dev server can call the {@code /api/**} endpoints during development.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Allows GET/POST/PUT requests to any {@code /api/**} endpoint from the local frontend dev
     * server origin.
     *
     * <p><b>Security-sensitive:</b> {@code allowCredentials(true)} lets the browser send/receive
     * the httpOnly auth cookie cross-origin; this requires an explicit (non-wildcard) origin,
     * which is already the case here.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry
                .addMapping("/api/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT")
                .allowedHeaders("Content-Type")
                .allowCredentials(true);
    }
}
