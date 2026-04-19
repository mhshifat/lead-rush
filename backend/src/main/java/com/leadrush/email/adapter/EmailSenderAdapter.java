package com.leadrush.email.adapter;

/**
 * Email sender adapter — swappable email sending implementations.
 *
 * Current impl: SmtpEmailSender (uses Spring Mail JavaMailSender)
 * Future impls: SendGrid, Resend, AWS SES, Postmark, Mailgun, etc.
 *
 * The service layer always depends on this INTERFACE, not a specific impl.
 * This is the Adapter Pattern — lets us swap providers with zero service changes.
 */
public interface EmailSenderAdapter {

    /**
     * Send an email via the specified mailbox credentials.
     * Returns a SendResult with status + messageId (for tracking).
     */
    SendResult send(SendRequest request);

    /**
     * Test SMTP connectivity without sending a real email.
     * Returns true if the connection + auth succeeded.
     */
    boolean testConnection(SmtpCredentials credentials);

    // ── Adapter-specific DTOs (domain-specific, not provider-specific) ──

    record SendRequest(
        SmtpCredentials credentials,
        String fromEmail,
        String fromName,
        String toEmail,
        String subject,
        String bodyHtml,
        String bodyText,
        java.util.Map<String, String> headers      // optional extra headers (e.g., List-Unsubscribe)
    ) {
        // Convenience constructor without headers
        public SendRequest(SmtpCredentials credentials, String fromEmail, String fromName,
                           String toEmail, String subject, String bodyHtml, String bodyText) {
            this(credentials, fromEmail, fromName, toEmail, subject, bodyHtml, bodyText, null);
        }
    }

    record SmtpCredentials(
        String host,
        int port,
        String username,
        String password,
        boolean useStartTls,
        boolean requireAuth
    ) {
        // Sensible default for real mail providers (Gmail, Outlook, SES).
        public SmtpCredentials(String host, int port, String username, String password) {
            this(host, port, username, password, true, true);
        }
    }

    record SendResult(
        boolean success,
        String messageId,       // RFC 5322 Message-ID (for tracking replies/bounces)
        String errorMessage
    ) {}
}
