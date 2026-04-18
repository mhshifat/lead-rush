package com.leadrush.notification.dto;

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
public class NotificationResponse {

    private UUID id;
    private String type;
    private String title;
    private String body;
    private String linkPath;

    /** JSONB passed through as raw JSON (not re-escaped). */
    @JsonRawValue
    private String metadata;

    private LocalDateTime readAt;
    private boolean read;
    private LocalDateTime createdAt;
}
