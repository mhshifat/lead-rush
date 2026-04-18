package com.leadrush.email.adapter.impl;

import com.leadrush.email.adapter.EmailSenderAdapter;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimeBodyPart;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.util.Properties;
import java.util.UUID;

/**
 * SMTP implementation of EmailSenderAdapter.
 *
 * Uses Jakarta Mail (the standard Java email library that's part of Spring Boot).
 * We create a fresh Session per send because each Mailbox has different credentials
 * (no global SMTP config — user-specific connections).
 *
 * STARTTLS is enabled for security (Gmail and Outlook require it).
 * Connection timeout is 10s so we don't hang forever if the server is unreachable.
 */
@Component
@Slf4j
public class SmtpEmailSender implements EmailSenderAdapter {

    @Override
    public SendResult send(SendRequest request) {
        try {
            Session session = createSession(request.credentials());
            MimeMessage message = new MimeMessage(session);

            // From (with display name)
            message.setFrom(new InternetAddress(request.fromEmail(), request.fromName() != null
                    ? request.fromName() : request.fromEmail()));

            // To
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(request.toEmail()));

            // Subject
            message.setSubject(request.subject(), "UTF-8");

            // Custom Message-ID (we generate so we can track it in bounce/reply handling later)
            String messageId = generateMessageId(request.fromEmail());
            message.setHeader("Message-ID", messageId);

            // Additional headers — e.g., List-Unsubscribe for RFC 8058 one-click unsubscribe
            if (request.headers() != null) {
                for (var entry : request.headers().entrySet()) {
                    message.setHeader(entry.getKey(), entry.getValue());
                }
            }

            // Body: use multipart (HTML + text fallback) if both are provided
            if (request.bodyHtml() != null && !request.bodyHtml().isBlank()) {
                MimeMultipart multipart = new MimeMultipart("alternative");

                // Text part (fallback)
                if (request.bodyText() != null && !request.bodyText().isBlank()) {
                    MimeBodyPart textPart = new MimeBodyPart();
                    textPart.setText(request.bodyText(), "UTF-8");
                    multipart.addBodyPart(textPart);
                }

                // HTML part
                MimeBodyPart htmlPart = new MimeBodyPart();
                htmlPart.setContent(request.bodyHtml(), "text/html; charset=UTF-8");
                multipart.addBodyPart(htmlPart);

                message.setContent(multipart);
            } else {
                // Plain text only
                message.setText(request.bodyText() != null ? request.bodyText() : "", "UTF-8");
            }

            // Send via SMTP
            Transport.send(message);

            log.info("Email sent: to={}, messageId={}", request.toEmail(), messageId);
            return new SendResult(true, messageId, null);

        } catch (MessagingException | UnsupportedEncodingException e) {
            log.warn("Failed to send email to {}: {}", request.toEmail(), e.getMessage());
            return new SendResult(false, null, e.getMessage());
        }
    }

    @Override
    public boolean testConnection(SmtpCredentials credentials) {
        try {
            Session session = createSession(credentials);
            Transport transport = session.getTransport("smtp");
            transport.connect(credentials.host(), credentials.port(),
                    credentials.username(), credentials.password());
            transport.close();
            return true;
        } catch (MessagingException e) {
            log.warn("SMTP connection test failed for {}: {}", credentials.host(), e.getMessage());
            return false;
        }
    }

    /** Create a JavaMail Session configured for STARTTLS. */
    private Session createSession(SmtpCredentials credentials) {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.host", credentials.host());
        props.put("mail.smtp.port", String.valueOf(credentials.port()));
        props.put("mail.smtp.connectiontimeout", "10000");   // 10 seconds
        props.put("mail.smtp.timeout", "10000");

        return Session.getInstance(props, new jakarta.mail.Authenticator() {
            @Override
            protected jakarta.mail.PasswordAuthentication getPasswordAuthentication() {
                return new jakarta.mail.PasswordAuthentication(
                        credentials.username(), credentials.password());
            }
        });
    }

    /** Generate a unique Message-ID header (RFC 5322 format). */
    private String generateMessageId(String fromEmail) {
        String domain = fromEmail.contains("@")
                ? fromEmail.substring(fromEmail.indexOf('@') + 1)
                : "lead-rush.local";
        return "<" + UUID.randomUUID() + "@" + domain + ">";
    }
}
