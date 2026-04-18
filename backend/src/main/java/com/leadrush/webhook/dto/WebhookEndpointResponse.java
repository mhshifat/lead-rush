package com.leadrush.webhook.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebhookEndpointResponse {

    private UUID id;
    private String url;
    private String description;
    private List<String> events;
    private boolean enabled;
    private int consecutiveFailures;
    private String disabledReason;
    private LocalDateTime lastSuccessAt;
    private LocalDateTime lastFailureAt;

    /** Only populated on the create response (one-time). */
    private String secret;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
