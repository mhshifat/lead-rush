package com.leadrush.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Typed binding for the `leadrush.*` config block. */
@Data
@Component
@ConfigurationProperties(prefix = "leadrush")
public class LeadRushProperties {

    private Jwt jwt = new Jwt();
    private String frontendUrl;
    private String encryptionKey;

    @Data
    public static class Jwt {
        private String secret;
        private long accessTokenExpiry;
        private long refreshTokenExpiry;
    }
}
