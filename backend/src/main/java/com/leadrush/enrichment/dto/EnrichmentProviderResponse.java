package com.leadrush.enrichment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for provider config. Never includes the decrypted API key.
 * Just reports whether a key is configured (hasApiKey flag).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EnrichmentProviderResponse {

    private UUID id;
    private String providerKey;
    private String displayName;
    private boolean enabled;
    private int priority;
    private boolean hasApiKey;
    private boolean requiresApiKey;

    private int callsThisMonth;
    private LocalDateTime lastUsedAt;
    private String lastError;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
