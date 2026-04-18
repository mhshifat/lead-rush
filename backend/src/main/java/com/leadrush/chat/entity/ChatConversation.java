package com.leadrush.chat.entity;

import com.leadrush.common.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One chat thread between a visitor and the workspace team.
 *
 * The visitor is identified by `visitor_token` — a UUID stored in their browser.
 * As long as they keep the same cookie/localStorage, subsequent messages thread
 * into this same conversation until the agent closes it.
 *
 * When the visitor submits an email, we create/find a Contact and bind `contact_id`.
 */
@Entity
@Table(name = "chat_conversations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatConversation extends TenantEntity {

    @Column(name = "visitor_token", nullable = false, unique = true, length = 64)
    private String visitorToken;

    @Column(name = "visitor_name")
    private String visitorName;

    @Column(name = "visitor_email")
    private String visitorEmail;

    @Column(name = "contact_id")
    private UUID contactId;

    /** OPEN | CLOSED */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.OPEN;

    @Column(name = "assigned_user_id")
    private UUID assignedUserId;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @Column(name = "last_message_preview", length = 500)
    private String lastMessagePreview;

    @Column(name = "unread_by_team", nullable = false)
    @Builder.Default
    private int unreadByTeam = 0;

    @Column(name = "unread_by_visitor", nullable = false)
    @Builder.Default
    private int unreadByVisitor = 0;

    @Column(name = "source_url", length = 2000)
    private String sourceUrl;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    public enum Status { OPEN, CLOSED }
}
