package com.leadrush.notification.entity;

import com.leadrush.common.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * In-app notification — delivered in real time via WebSocket AND persisted
 * so users see it when they return later.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification extends TenantEntity {

    /** Recipient user. Every notification has exactly one target. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String body;

    /** Deep-link path the UI navigates to when clicked (e.g., /contacts/xyz). */
    @Column(name = "link_path")
    private String linkPath;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private String metadata = "{}";

    @Column(name = "read_at")
    private LocalDateTime readAt;

    public boolean isRead() {
        return readAt != null;
    }
}
