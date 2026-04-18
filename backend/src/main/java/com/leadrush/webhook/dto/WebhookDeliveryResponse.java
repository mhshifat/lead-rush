package com.leadrush.webhook.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebhookDeliveryResponse {

    private UUID id;
    private UUID endpointId;
    private String eventType;
    private UUID eventId;

    @JsonRawValue
    private String payload;

    private String status;
    private int attemptCount;
    private Integer lastStatusCode;
    private String lastError;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime lastAttemptAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime createdAt;
}
