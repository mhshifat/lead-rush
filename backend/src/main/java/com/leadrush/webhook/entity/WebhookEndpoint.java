package com.leadrush.webhook.entity;

import com.leadrush.common.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Customer-configured webhook destination. One row per URL per workspace.
 * The `events` column stores a comma-separated list of WebhookEventType topics,
 * or "*" to subscribe to everything. Kept as text because the set is small
 * and querying-by-event isn't on the hot path — we filter in Java.
 */
@Entity
@Table(name = "webhook_endpoints")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "secret")
public class WebhookEndpoint extends TenantEntity {

    @Column(nullable = false, length = 2000)
    private String url;

    @Column(length = 500)
    private String description;

    /** HMAC secret. Shown to the user once on creation; used to sign every payload. */
    @Column(nullable = false, length = 128)
    private String secret;

    @Column(nullable = false, columnDefinition = "text")
    private String events;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "consecutive_failures", nullable = false)
    @Builder.Default
    private int consecutiveFailures = 0;

    @Column(name = "disabled_reason", length = 500)
    private String disabledReason;

    @Column(name = "last_success_at")
    private LocalDateTime lastSuccessAt;

    @Column(name = "last_failure_at")
    private LocalDateTime lastFailureAt;

    // ── Helpers ──

    /** Check if this endpoint has subscribed to a given event type. */
    public boolean subscribesTo(WebhookEventType eventType) {
        if (events == null || events.isBlank()) return false;
        String topic = eventType.topic();
        for (String raw : events.split(",")) {
            String trimmed = raw.trim();
            if (trimmed.equals("*") || trimmed.equals(topic)) return true;
        }
        return false;
    }

    public List<String> subscribedTopics() {
        if (events == null || events.isBlank()) return List.of();
        return Arrays.stream(events.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
