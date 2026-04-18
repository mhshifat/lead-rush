package com.leadrush.email.entity;

import com.leadrush.common.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

// Connected sending mailbox. credentialsEncrypted holds the SMTP password under AES-256-GCM.
@Entity
@Table(name = "mailboxes",
       uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "email"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"credentialsEncrypted"})
public class Mailbox extends TenantEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MailboxProvider provider;

    @Column(name = "smtp_host")
    private String smtpHost;

    @Column(name = "smtp_port")
    private Integer smtpPort;

    @Column(name = "smtp_username")
    private String smtpUsername;

    @Column(name = "credentials_encrypted", nullable = false, columnDefinition = "text")
    private String credentialsEncrypted;

    @Column(name = "daily_limit")
    @Builder.Default
    private Integer dailyLimit = 100;

    @Column(name = "sends_today")
    @Builder.Default
    private Integer sendsToday = 0;

    @Column(name = "sends_today_date")
    private LocalDate sendsTodayDate;

    // Warmup: gradual daily-volume ramp to build sender reputation.
    @Column(name = "warmup_enabled")
    @Builder.Default
    private boolean warmupEnabled = false;

    @Column(name = "warmup_current_daily")
    @Builder.Default
    private Integer warmupCurrentDaily = 5;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MailboxStatus status = MailboxStatus.ACTIVE;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "last_tested_at")
    private LocalDateTime lastTestedAt;

    // IMAP reply detection. Password reuses credentialsEncrypted (same account).
    @Column(name = "imap_host")
    private String imapHost;

    @Column(name = "imap_port")
    private Integer imapPort;

    @Column(name = "imap_username")
    private String imapUsername;

    @Column(name = "reply_detection_enabled", nullable = false)
    @Builder.Default
    private boolean replyDetectionEnabled = false;

    /** Highest UID the poller has processed — only UIDs above this are scanned each tick. */
    @Column(name = "imap_last_seen_uid")
    private Long imapLastSeenUid;

    @Column(name = "imap_last_error", columnDefinition = "text")
    private String imapLastError;

    @Column(name = "imap_last_polled_at")
    private LocalDateTime imapLastPolledAt;

    /** Returns true if the mailbox is still under its daily limit. Rolls over on date change. */
    public boolean canSendToday() {
        LocalDate today = LocalDate.now();
        if (sendsTodayDate == null || !sendsTodayDate.equals(today)) {
            sendsTodayDate = today;
            sendsToday = 0;
        }
        return sendsToday < dailyLimit;
    }

    public void recordSend() {
        canSendToday();
        sendsToday++;
    }
}
