package com.leadrush.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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
        CorsConfiguration app = new CorsConfiguration();
        app.setAllowedOrigins(List.of(properties.getFrontendUrl()));
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
