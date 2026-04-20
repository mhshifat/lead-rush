package com.leadrush.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class CorsConfig {

    private final LeadRushProperties properties;

    // Spring Security's .cors(...) picks this up automatically, which plugs the
    // CORS filter in BEFORE the auth checks — so preflight OPTIONS requests
    // on authenticated endpoints don't get 403'd (no CORS headers on a 403
    // shows up as a generic "CORS error" in the browser).
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // FRONTEND_URL may be a single origin or a comma-separated list. The list
        // form is how Vercel preview deployments (per-branch URLs) get allowed
        // alongside the production domain — e.g. "https://app.leadrush.com,https://*.vercel.app".
        // Entries with "*" are treated as patterns; plain URLs go through as exact origins.
        List<String> configured = Arrays.stream(properties.getFrontendUrl().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        List<String> exactOrigins = configured.stream().filter(s -> !s.contains("*")).toList();
        List<String> patternOrigins = configured.stream().filter(s -> s.contains("*")).toList();

        CorsConfiguration app = new CorsConfiguration();
        if (!exactOrigins.isEmpty()) app.setAllowedOrigins(exactOrigins);
        if (!patternOrigins.isEmpty()) app.setAllowedOriginPatterns(patternOrigins);
        app.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        app.setAllowedHeaders(List.of("*"));
        app.setAllowCredentials(true);
        app.setMaxAge(3600L);

        // Public surface (widget, public chat, /widget.js): any origin, token-scoped.
        CorsConfiguration publicCors = new CorsConfiguration();
        publicCors.setAllowedOriginPatterns(List.of("*"));
        publicCors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        publicCors.setAllowedHeaders(List.of("Content-Type", "Accept"));
        publicCors.setAllowCredentials(false);
        publicCors.setMaxAge(3600L);

        // Extension surface (/api/v1/ext/**): the browser extension runs on a
        // chrome-extension:// origin that varies per install. Allow any
        // chrome-extension/moz-extension origin — access is still guarded by
        // the X-API-Key header inside the request.
        CorsConfiguration extensionCors = new CorsConfiguration();
        extensionCors.setAllowedOriginPatterns(List.of(
                "chrome-extension://*",
                "moz-extension://*"
        ));
        extensionCors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        extensionCors.setAllowedHeaders(List.of("Content-Type", "Accept", "X-API-Key"));
        extensionCors.setAllowCredentials(false);
        extensionCors.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/public/**", publicCors);
        source.registerCorsConfiguration("/api/v1/ext/**", extensionCors);
        source.registerCorsConfiguration("/widget.js", publicCors);
        source.registerCorsConfiguration("/**", app);
        return source;
    }
}
