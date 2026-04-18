package com.leadrush.email.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Mailbox response DTO — NEVER includes encrypted credentials.
 * Only shows enough info for the UI to display and manage mailboxes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MailboxResponse {

    private UUID id;
    private String name;
    private String email;
    private String provider;
    private String smtpHost;
    private Integer smtpPort;
    private String smtpUsername;

    private Integer dailyLimit;
    private Integer sendsToday;

    private String status;
    private String lastError;
    private LocalDateTime lastTestedAt;

    // IMAP reply detection
    private String imapHost;
    private Integer imapPort;
    private String imapUsername;
    private Boolean replyDetectionEnabled;
    private String imapLastError;
    private LocalDateTime imapLastPolledAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
