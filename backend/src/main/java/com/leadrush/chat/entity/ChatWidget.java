package com.leadrush.chat.entity;

import com.leadrush.common.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Per-workspace chat widget config. Exactly one row per workspace (unique index on workspace_id).
 *
 * Served publicly at GET /api/v1/public/chat/widget/{workspaceSlug} so the embedded widget
 * on a customer's site can style itself without needing API credentials.
 */
@Entity
@Table(name = "chat_widgets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatWidget extends TenantEntity {

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "display_name", nullable = false)
    @Builder.Default
    private String displayName = "Support";

    @Column(nullable = false, columnDefinition = "text")
    @Builder.Default
    private String greeting = "Hi there! How can we help?";

    @Column(name = "offline_message", nullable = false, columnDefinition = "text")
    @Builder.Default
    private String offlineMessage = "We're offline — leave an email and we'll get back to you.";

    @Column(name = "primary_color", nullable = false)
    @Builder.Default
    private String primaryColor = "#5E6AD2";

    /** BOTTOM_RIGHT | BOTTOM_LEFT */
    @Column(nullable = false)
    @Builder.Default
    private String position = "BOTTOM_RIGHT";

    @Column(name = "require_email", nullable = false)
    @Builder.Default
    private boolean requireEmail = true;
}
