package com.leadrush.email.service;

import com.leadrush.common.exception.BusinessException;
import com.leadrush.common.exception.ResourceNotFoundException;
import com.leadrush.email.adapter.EmailSenderAdapter;
import com.leadrush.email.dto.CreateMailboxRequest;
import com.leadrush.email.dto.MailboxResponse;
import com.leadrush.email.entity.Mailbox;
import com.leadrush.email.entity.MailboxProvider;
import com.leadrush.email.entity.MailboxStatus;
import com.leadrush.email.repository.MailboxRepository;
import com.leadrush.security.EncryptionService;
import com.leadrush.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Mailbox service — manages connected sending accounts.
 *
 * SECURITY FLOW:
 *   1. User submits password via API
 *   2. We test the SMTP connection with plaintext password
 *   3. If successful, encrypt the password with AES-256-GCM
 *   4. Store ONLY the encrypted version in the database
 *   5. To send an email: decrypt on-demand, use briefly, discard from memory
 *
 * We never log passwords and never return them in responses.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MailboxService {

    private final MailboxRepository mailboxRepository;
    private final EmailSenderAdapter emailSender;
    private final EncryptionService encryptionService;

    @Transactional(readOnly = true)
    public List<MailboxResponse> listMailboxes() {
        UUID workspaceId = TenantContext.getWorkspaceId();
        return mailboxRepository.findByWorkspaceId(workspaceId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MailboxResponse getMailbox(UUID id) {
        UUID workspaceId = TenantContext.getWorkspaceId();
        Mailbox mailbox = mailboxRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Mailbox", id));
        return toResponse(mailbox);
    }

    /**
     * Connect a new mailbox.
     * Tests the SMTP connection first — if it fails, don't save to DB.
     */
    @Transactional
    public MailboxResponse connectMailbox(CreateMailboxRequest request) {
        UUID workspaceId = TenantContext.getWorkspaceId();

        // Test SMTP connection BEFORE saving
        var credentials = new EmailSenderAdapter.SmtpCredentials(
                request.getSmtpHost(),
                request.getSmtpPort(),
                request.getSmtpUsername(),
                request.getSmtpPassword()
        );

        boolean connected = emailSender.testConnection(credentials);
        if (!connected) {
            throw new BusinessException(
                "Could not connect to SMTP server. Please check the host, port, username, and password.");
        }

        // Encrypt password before saving
        String encryptedPassword = encryptionService.encrypt(request.getSmtpPassword());

        Mailbox mailbox = Mailbox.builder()
                .name(request.getName())
                .email(request.getEmail())
                .provider(MailboxProvider.valueOf(request.getProvider()))
                .smtpHost(request.getSmtpHost())
                .smtpPort(request.getSmtpPort())
                .smtpUsername(request.getSmtpUsername())
                .credentialsEncrypted(encryptedPassword)
                .dailyLimit(request.getDailyLimit() != null ? request.getDailyLimit() : 100)
                .status(MailboxStatus.ACTIVE)
                .lastTestedAt(LocalDateTime.now())
                .imapHost(request.getImapHost())
                .imapPort(request.getImapPort())
                .imapUsername(request.getImapUsername())
                .replyDetectionEnabled(Boolean.TRUE.equals(request.getReplyDetectionEnabled()))
                .build();
        mailbox.setWorkspaceId(workspaceId);

        mailbox = mailboxRepository.save(mailbox);
        log.info("Mailbox connected: {} (id: {})", mailbox.getEmail(), mailbox.getId());

        return toResponse(mailbox);
    }

    /**
     * Test an existing mailbox's SMTP connection.
     * Updates the status based on the result.
     */
    @Transactional
    public boolean testMailbox(UUID id) {
        UUID workspaceId = TenantContext.getWorkspaceId();
        Mailbox mailbox = mailboxRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Mailbox", id));

        String password = encryptionService.decrypt(mailbox.getCredentialsEncrypted());
        var credentials = new EmailSenderAdapter.SmtpCredentials(
                mailbox.getSmtpHost(),
                mailbox.getSmtpPort(),
                mailbox.getSmtpUsername(),
                password
        );

        boolean connected = emailSender.testConnection(credentials);
        mailbox.setLastTestedAt(LocalDateTime.now());

        if (connected) {
            mailbox.setStatus(MailboxStatus.ACTIVE);
            mailbox.setLastError(null);
        } else {
            mailbox.setStatus(MailboxStatus.ERROR);
            mailbox.setLastError("Connection test failed");
        }

        mailboxRepository.save(mailbox);
        return connected;
    }

    @Transactional
    public void deleteMailbox(UUID id) {
        UUID workspaceId = TenantContext.getWorkspaceId();
        Mailbox mailbox = mailboxRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Mailbox", id));
        mailboxRepository.delete(mailbox);
        log.info("Mailbox deleted: {} (id: {})", mailbox.getEmail(), id);
    }

    /**
     * INTERNAL: decrypt credentials for sending.
     * Called by SequenceExecutionService when sending emails.
     * NEVER exposed to any controller.
     */
    public EmailSenderAdapter.SmtpCredentials getDecryptedCredentials(Mailbox mailbox) {
        String password = encryptionService.decrypt(mailbox.getCredentialsEncrypted());
        return new EmailSenderAdapter.SmtpCredentials(
                mailbox.getSmtpHost(),
                mailbox.getSmtpPort(),
                mailbox.getSmtpUsername(),
                password
        );
    }

    // ── Response mapping ──

    private MailboxResponse toResponse(Mailbox mailbox) {
        return MailboxResponse.builder()
                .id(mailbox.getId())
                .name(mailbox.getName())
                .email(mailbox.getEmail())
                .provider(mailbox.getProvider().name())
                .smtpHost(mailbox.getSmtpHost())
                .smtpPort(mailbox.getSmtpPort())
                .smtpUsername(mailbox.getSmtpUsername())
                .dailyLimit(mailbox.getDailyLimit())
                .sendsToday(mailbox.getSendsToday())
                .status(mailbox.getStatus().name())
                .lastError(mailbox.getLastError())
                .lastTestedAt(mailbox.getLastTestedAt())
                .imapHost(mailbox.getImapHost())
                .imapPort(mailbox.getImapPort())
                .imapUsername(mailbox.getImapUsername())
                .replyDetectionEnabled(mailbox.isReplyDetectionEnabled())
                .imapLastError(mailbox.getImapLastError())
                .imapLastPolledAt(mailbox.getImapLastPolledAt())
                .createdAt(mailbox.getCreatedAt())
                .updatedAt(mailbox.getUpdatedAt())
                .build();
    }
}
