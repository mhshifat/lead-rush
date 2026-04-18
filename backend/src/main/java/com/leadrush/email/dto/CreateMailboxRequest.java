package com.leadrush.email.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateMailboxRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Provider is required")
    private String provider;            // SMTP, GMAIL, OUTLOOK

    @NotBlank(message = "SMTP host is required")
    private String smtpHost;

    @NotNull(message = "SMTP port is required")
    private Integer smtpPort;

    @NotBlank(message = "SMTP username is required")
    private String smtpUsername;

    @NotBlank(message = "SMTP password is required")
    private String smtpPassword;

    private Integer dailyLimit;         // optional, default 100

    // ── IMAP (optional — only required when reply detection is on) ──
    private String imapHost;
    private Integer imapPort;
    private String imapUsername;
    private Boolean replyDetectionEnabled;
}
