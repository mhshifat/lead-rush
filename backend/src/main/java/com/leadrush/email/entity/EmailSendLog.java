package com.leadrush.email.entity;

import com.leadrush.common.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Low-level record of every email sent through the platform.
 * Used for audit, debugging, analytics, and deliverability tracking.
 */
@Entity
@Table(name = "email_send_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailSendLog extends TenantEntity {

    @Column(name = "mailbox_id", nullable = false)
    private UUID mailboxId;

    @Column(name = "contact_id")
    private UUID contactId;

    @Column(name = "step_execution_id")
    private UUID stepExecutionId;

    @Column(name = "to_email", nullable = false)
    private String toEmail;

    @Column(name = "from_email", nullable = false)
    private String fromEmail;

    @Column(nullable = false, length = 500)
    private String subject;

    @Column(name = "body_html", columnDefinition = "text")
    private String bodyHtml;

    @Column(name = "message_id", length = 500)
    private String messageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SendStatus status;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    public enum SendStatus {
        QUEUED,     // Created but not yet sent
        SENT,       // Successfully handed to SMTP server
        FAILED,     // SMTP send failed
        BOUNCED     // Delivery bounce received (Phase 3)
    }
}
