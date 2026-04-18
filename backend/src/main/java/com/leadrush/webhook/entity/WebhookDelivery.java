package com.leadrush.webhook.entity;

import com.leadrush.common.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row per event × endpoint. The scheduled worker POSTs it, records the
 * HTTP outcome, bumps the attempt counter, and schedules a retry with
 * exponential backoff. After MAX_ATTEMPTS, status flips to ABANDONED.
 */
@Entity
@Table(name = "webhook_deliveries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookDelivery extends TenantEntity {

    @Column(name = "endpoint_id", nullable = false)
    private UUID endpointId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    /** Stable per-event ID. Lives in the delivered payload's `id` field so
     *  receivers can dedupe on retries. */
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    @Column(name = "next_attempt_at", nullable = false)
    @Builder.Default
    private LocalDateTime nextAttemptAt = LocalDateTime.now();

    @Column(name = "last_status_code")
    private Integer lastStatusCode;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "abandoned_at")
    private LocalDateTime abandonedAt;

    public enum Status {
        PENDING, IN_PROGRESS, SUCCEEDED, FAILED, ABANDONED
    }
}
