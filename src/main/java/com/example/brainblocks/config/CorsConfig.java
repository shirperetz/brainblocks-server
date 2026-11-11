package com.example.brainblocks.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /**
     * 2026-11-11: the public address (Tailscale Funnel) - behind the proxy the server sees
     * http://127.0.0.1:8080, so the browser's Origin https://<name>.ts.net looked cross-origin and
     * every sign-up / log-in from the public link got 403. Kept in local.properties (not in git:
     * the repo is public), comma-separated.
     */
    private final String extraOrigins;

    public CorsConfig(@Value("${brainblocks.cors.extra-origins:}") String extraOrigins) {
        this.extraOrigins = extraOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = new ArrayList<>(List.of(
                // עולם החשבון (learn-client) only - the Math Race client was removed 2026-11-11.
                "http://localhost:5174", "http://127.0.0.1:5174"));
        Arrays.stream(extraOrigins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).forEach(origins::add);
        registry.addMapping("/api/**")
                .allowedOrigins(origins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false);
    }
}
