package com.leadrush.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class CorsConfig {

    private final LeadRushProperties properties;

    @Bean
    public CorsFilter corsFilter() {
        // Authenticated surface: only the configured frontend origin.
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

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/public/**", publicCors);
        source.registerCorsConfiguration("/widget.js", publicCors);
        source.registerCorsConfiguration("/**", app);
        return new CorsFilter(source);
    }
}
