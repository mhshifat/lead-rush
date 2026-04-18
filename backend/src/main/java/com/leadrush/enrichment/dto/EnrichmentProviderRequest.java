package com.leadrush.enrichment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EnrichmentProviderRequest {

    @NotBlank(message = "Provider key is required")
    private String providerKey;         // HUNTER, MOCK, etc.

    private String apiKey;              // plaintext — we encrypt before storing

    private Boolean enabled;
    private Integer priority;
}
