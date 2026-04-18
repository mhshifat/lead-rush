package com.leadrush.chat.entity;

import com.leadrush.common.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * One message in a chat conversation.
 * Ordered by createdAt — we don't use a separate position column.
 */
@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage extends TenantEntity {

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Sender sender;

    /** Populated only when sender = AGENT. */
    @Column(name = "agent_user_id")
    private UUID agentUserId;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    public enum Sender { VISITOR, AGENT, SYSTEM }
}
