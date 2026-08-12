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
     * Allows GET requests to any {@code /api/**} endpoint from the local frontend dev server origin.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins("http://localhost:5173").allowedMethods("GET");
    }
}
