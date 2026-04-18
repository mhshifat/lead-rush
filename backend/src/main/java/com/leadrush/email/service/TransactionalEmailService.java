package com.leadrush.email.service;

import com.leadrush.config.LeadRushProperties;
import com.leadrush.email.adapter.EmailSenderAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Sends TRANSACTIONAL emails (activation, password reset, invites) — NOT sequence outreach.
 *
 * DIFFERENCE FROM SEQUENCE EMAILS:
 *   - Uses the platform's SMTP config (from application.yml) — NOT a user's mailbox
 *   - No tracking pixel, no click rewriting, no unsubscribe footer
 *   - Sent synchronously as part of the request flow (e.g., during registration)
 *   - Goes through the platform's "noreply" address (e.g., noreply@leadrush.com)
 *
 * The SMTP credentials come from the app's environment:
 *   spring.mail.host, spring.mail.username, spring.mail.password
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionalEmailService {

    private final EmailSenderAdapter emailSender;
    private final LeadRushProperties properties;

    @Value("${spring.mail.host:}")
    private String smtpHost;

    @Value("${spring.mail.port:587}")
    private int smtpPort;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    @Value("${spring.mail.password:}")
    private String smtpPassword;

    @Value("${leadrush.email.from-address:noreply@leadrush.local}")
    private String fromAddress;

    @Value("${leadrush.email.from-name:Lead Rush}")
    private String fromName;

    /**
     * Send the account activation email after registration.
     */
    public void sendActivationEmail(String recipientEmail, String recipientName, String activationToken) {
        String activationUrl = properties.getFrontendUrl() + "/auth/verify-email?token=" + activationToken;

        String subject = "Activate your Lead Rush account";
        String bodyHtml = buildActivationHtml(recipientName, activationUrl);
        String bodyText = "Hi " + recipientName + ",\n\n"
                + "Welcome to Lead Rush! Click the link below to activate your account:\n\n"
                + activationUrl + "\n\n"
                + "This link expires in 24 hours.\n\n"
                + "If you didn't create an account, you can safely ignore this email.";

        sendTransactional(recipientEmail, subject, bodyHtml, bodyText);
    }

    /**
     * Send a workspace invitation email. The recipient may or may not have an existing account —
     * the accept-invite page on the frontend handles both paths.
     */
    public void sendInvitationEmail(String recipientEmail, String workspaceName, String inviterName, String token) {
        String acceptUrl = properties.getFrontendUrl() + "/invite/accept?token=" + token;

        String subject = inviterName + " invited you to join " + workspaceName + " on Lead Rush";
        String bodyHtml = buildInvitationHtml(workspaceName, inviterName, acceptUrl);
        String bodyText = inviterName + " invited you to join the " + workspaceName + " workspace on Lead Rush.\n\n"
                + "Accept the invitation: " + acceptUrl + "\n\n"
                + "This invitation expires in 7 days.";

        sendTransactional(recipientEmail, subject, bodyHtml, bodyText);
    }

    /**
     * Internal send method — wraps EmailSenderAdapter with platform credentials.
     *
     * If SMTP isn't configured (username/password empty), logs a warning and skips sending
     * so local dev doesn't fail just because SMTP isn't set up.
     */
    private void sendTransactional(String to, String subject, String bodyHtml, String bodyText) {
        if (smtpUsername == null || smtpUsername.isBlank()) {
            log.warn("SMTP is not configured — skipping transactional email to {}. "
                    + "Set SMTP_USERNAME + SMTP_PASSWORD env vars to enable.", to);
            return;
        }

        var credentials = new EmailSenderAdapter.SmtpCredentials(
                smtpHost, smtpPort, smtpUsername, smtpPassword);

        var request = new EmailSenderAdapter.SendRequest(
                credentials, fromAddress, fromName, to, subject, bodyHtml, bodyText);

        EmailSenderAdapter.SendResult result = emailSender.send(request);

        if (result.success()) {
            log.info("Transactional email sent to {}: {}", to, subject);
        } else {
            log.warn("Failed to send transactional email to {}: {}", to, result.errorMessage());
        }
    }

    private String buildInvitationHtml(String workspaceName, String inviterName, String acceptUrl) {
        return """
                <!DOCTYPE html>
                <html><body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; max-width: 600px; margin: 40px auto; color: #333; line-height: 1.5;">
                  <h1 style="color: #111;">You've been invited to %s</h1>
                  <p><strong>%s</strong> invited you to collaborate on the <strong>%s</strong> workspace on Lead Rush.</p>
                  <p style="margin: 30px 0;">
                    <a href="%s" style="background: #111; color: white; padding: 12px 24px; text-decoration: none; border-radius: 6px; display: inline-block;">
                      Accept invitation
                    </a>
                  </p>
                  <p style="font-size: 13px; color: #888;">Or paste this link into your browser:<br><a href="%s" style="color: #666;">%s</a></p>
                  <p style="font-size: 13px; color: #888;">This invitation expires in 7 days.</p>
                </body></html>
                """.formatted(workspaceName, inviterName, workspaceName, acceptUrl, acceptUrl, acceptUrl);
    }

    private String buildActivationHtml(String name, String activationUrl) {
        return """
                <!DOCTYPE html>
                <html><body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; max-width: 600px; margin: 40px auto; color: #333; line-height: 1.5;">
                  <h1 style="color: #111;">Welcome to Lead Rush, %s!</h1>
                  <p>Click the button below to activate your account:</p>
                  <p style="margin: 30px 0;">
                    <a href="%s" style="background: #111; color: white; padding: 12px 24px; text-decoration: none; border-radius: 6px; display: inline-block;">
                      Activate my account
                    </a>
                  </p>
                  <p style="font-size: 13px; color: #888;">
                    Or paste this link into your browser:<br>
                    <a href="%s" style="color: #666;">%s</a>
                  </p>
                  <p style="font-size: 13px; color: #888;">This link expires in 24 hours. If you didn't create an account, you can safely ignore this email.</p>
                </body></html>
                """.formatted(name, activationUrl, activationUrl, activationUrl);
    }
}
