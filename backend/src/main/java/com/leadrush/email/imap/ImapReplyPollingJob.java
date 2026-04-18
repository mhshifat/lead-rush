package com.leadrush.email.imap;

import com.leadrush.email.entity.Mailbox;
import com.leadrush.email.entity.MailboxStatus;
import com.leadrush.email.repository.EmailSendLogRepository;
import com.leadrush.email.repository.MailboxRepository;
import com.leadrush.leadscoring.entity.TriggerType;
import com.leadrush.leadscoring.service.LeadScoringService;
import com.leadrush.security.EncryptionService;
import com.leadrush.sequence.entity.EnrollmentStatus;
import com.leadrush.sequence.entity.SequenceEnrollment;
import com.leadrush.sequence.entity.SequenceStepExecution;
import com.leadrush.sequence.repository.SequenceEnrollmentRepository;
import com.leadrush.sequence.repository.SequenceStepExecutionRepository;
import com.leadrush.webhook.entity.WebhookEventType;
import com.leadrush.webhook.service.WebhookService;
import jakarta.mail.Address;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * Reply detection — polls enabled mailboxes' IMAP INBOX for new messages, matches each
 * message's In-Reply-To / References header against email_send_logs.message_id, and when
 * matched:
 *   1. Sets sequence_step_executions.replied_at
 *   2. Flips the enrollment to REPLIED
 *   3. Fires the EMAIL_REPLIED scoring trigger
 *   4. Publishes the `email.replied` webhook event
 *
 * Runs every 3 minutes. Uses IMAP UIDs so each poll only scans new messages.
 *
 * IMPORTANT: Jakarta Mail IMAP is blocking I/O — each mailbox is processed serially in
 * its own transaction so one slow server can't stall others indefinitely. For scale we'd
 * move this onto a virtual-thread-per-mailbox executor.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ImapReplyPollingJob {

    private final MailboxRepository mailboxRepository;
    private final EmailSendLogRepository sendLogRepository;
    private final SequenceStepExecutionRepository executionRepository;
    private final SequenceEnrollmentRepository enrollmentRepository;
    private final EncryptionService encryptionService;
    private final LeadScoringService leadScoringService;
    private final WebhookService webhookService;
    // Self-injection via a lazy proxy so Spring doesn't see a circular bean graph at
    // startup. We need the proxy so @Transactional actually wraps each pollOne() call;
    // calling it directly from runBatch() would bypass the transaction interceptor.
    @Autowired
    @Lazy
    private ImapReplyPollingJob self;

    /** Runs every 3 minutes. Initial delay lets the app boot before the first scan. */
    @Scheduled(fixedDelay = 180_000, initialDelay = 60_000)
    public void pollAll() {
        List<Mailbox> mailboxes;
        try {
            mailboxes = mailboxRepository.findAll().stream()
                    .filter(Mailbox::isReplyDetectionEnabled)
                    .filter(m -> m.getStatus() == MailboxStatus.ACTIVE)
                    .filter(m -> m.getImapHost() != null && !m.getImapHost().isBlank())
                    .toList();
        } catch (Exception e) {
            log.warn("IMAP poll query failed: {}", e.getMessage());
            return;
        }
        if (mailboxes.isEmpty()) return;

        log.debug("IMAP poll: scanning {} mailboxes", mailboxes.size());
        for (Mailbox mailbox : mailboxes) {
            try {
                self.pollOne(mailbox.getId());
            } catch (Exception e) {
                log.warn("IMAP poll for mailbox {} threw: {}", mailbox.getId(), e.getMessage());
            }
        }
    }

    @Transactional
    public void pollOne(java.util.UUID mailboxId) {
        Mailbox mailbox = mailboxRepository.findById(mailboxId).orElse(null);
        if (mailbox == null || !mailbox.isReplyDetectionEnabled()) return;

        Store store = null;
        Folder inbox = null;
        try {
            store = openInbox(mailbox);
            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);

            UIDFolder uidFolder = (UIDFolder) inbox;
            long startUid = mailbox.getImapLastSeenUid() != null
                    ? mailbox.getImapLastSeenUid() + 1
                    : highestUid(inbox, uidFolder);   // seed: skip existing INBOX contents

            // Fetch everything above the last-seen UID — bounded because IMAP servers
            // index UIDs contiguously per folder.
            Message[] newMessages = uidFolder.getMessagesByUID(startUid, UIDFolder.LASTUID);
            long highestSeen = mailbox.getImapLastSeenUid() != null ? mailbox.getImapLastSeenUid() : startUid - 1;

            for (Message msg : newMessages) {
                long uid;
                try { uid = uidFolder.getUID(msg); } catch (Exception e) { continue; }
                if (uid <= highestSeen) continue;   // already processed
                highestSeen = Math.max(highestSeen, uid);

                processMessage(mailbox, msg);
            }

            mailbox.setImapLastSeenUid(highestSeen);
            mailbox.setImapLastPolledAt(LocalDateTime.now());
            mailbox.setImapLastError(null);
            mailboxRepository.save(mailbox);

        } catch (Exception e) {
            log.warn("IMAP poll failed for {}: {}", mailbox.getEmail(), e.getMessage());
            mailbox.setImapLastError(truncate(e.getMessage(), 1000));
            mailbox.setImapLastPolledAt(LocalDateTime.now());
            mailboxRepository.save(mailbox);
        } finally {
            closeSilently(inbox);
            closeSilently(store);
        }
    }

    // ── Message matching + event firing ──

    private void processMessage(Mailbox mailbox, Message msg) throws Exception {
        String inReplyTo = firstHeader(msg, "In-Reply-To");
        String references = firstHeader(msg, "References");

        String parentCandidate = cleanMessageId(inReplyTo);
        if (parentCandidate == null) parentCandidate = lastReferenceId(references);
        if (parentCandidate == null) return;
        final String parentMessageId = parentCandidate;

        // Find the original outbound email by Message-ID
        Optional<com.leadrush.email.entity.EmailSendLog> parentLog =
                sendLogRepository.findAll().stream()
                        .filter(log -> parentMessageId.equals(log.getMessageId()))
                        .filter(log -> mailbox.getWorkspaceId().equals(log.getWorkspaceId()))
                        .findFirst();
        if (parentLog.isEmpty()) return;

        java.util.UUID executionId = parentLog.get().getStepExecutionId();
        if (executionId == null) return;

        Optional<SequenceStepExecution> execOpt = executionRepository.findById(executionId);
        if (execOpt.isEmpty()) return;
        SequenceStepExecution exec = execOpt.get();
        if (exec.getRepliedAt() != null) return;   // already recorded

        // 1. Mark the execution as replied
        exec.setRepliedAt(LocalDateTime.now());
        executionRepository.save(exec);

        // 2. Flip the enrollment to REPLIED so the sequence stops
        Optional<SequenceEnrollment> enrollmentOpt = enrollmentRepository.findById(exec.getEnrollmentId());
        java.util.UUID contactId = parentLog.get().getContactId();
        if (enrollmentOpt.isPresent()) {
            SequenceEnrollment enrollment = enrollmentOpt.get();
            if (enrollment.getStatus() == EnrollmentStatus.ACTIVE) {
                enrollment.setStatus(EnrollmentStatus.REPLIED);
                enrollment.setNextExecutionAt(null);
                enrollmentRepository.save(enrollment);
            }
            contactId = enrollment.getContact().getId();
        }

        // 3. Scoring trigger
        if (contactId != null) {
            leadScoringService.fireTrigger(mailbox.getWorkspaceId(),
                    TriggerType.EMAIL_REPLIED, contactId,
                    "Replied to email from " + fromAddress(msg));
        }

        // 4. Webhook
        webhookService.publish(mailbox.getWorkspaceId(), WebhookEventType.EMAIL_REPLIED, java.util.Map.of(
                "executionId", exec.getId(),
                "enrollmentId", exec.getEnrollmentId(),
                "contactId", contactId != null ? contactId : "",
                "mailboxId", mailbox.getId(),
                "subject", safeSubject(msg),
                "fromAddress", fromAddress(msg)
        ));

        log.info("Reply detected: mailbox={} from={} matched execution={}",
                mailbox.getEmail(), fromAddress(msg), exec.getId());
    }

    // ── IMAP session helpers ──

    private Store openInbox(Mailbox mailbox) throws Exception {
        String host = mailbox.getImapHost();
        int port = mailbox.getImapPort() != null ? mailbox.getImapPort() : 993;
        String username = mailbox.getImapUsername() != null && !mailbox.getImapUsername().isBlank()
                ? mailbox.getImapUsername() : mailbox.getEmail();
        String password = encryptionService.decrypt(mailbox.getCredentialsEncrypted());

        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", host);
        props.put("mail.imaps.port", String.valueOf(port));
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.connectiontimeout", "15000");
        props.put("mail.imaps.timeout", "15000");

        Session session = Session.getInstance(props);
        Store store = session.getStore("imaps");
        store.connect(host, port, username, password);
        return store;
    }

    private long highestUid(Folder inbox, UIDFolder uidFolder) throws Exception {
        int count = inbox.getMessageCount();
        if (count == 0) return 0L;
        Message last = inbox.getMessage(count);
        return uidFolder.getUID(last);
    }

    private static String firstHeader(Message msg, String name) {
        try {
            String[] values = msg.getHeader(name);
            return (values != null && values.length > 0) ? values[0] : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** "<abc@example.com>" → "abc@example.com". Tolerates bare IDs too. */
    private static String cleanMessageId(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    /** References is space-separated "<id1> <id2> ..." — the one we replied to is the last. */
    private static String lastReferenceId(String references) {
        if (references == null || references.isBlank()) return null;
        String[] parts = references.trim().split("\\s+");
        return cleanMessageId(parts[parts.length - 1]);
    }

    private static String fromAddress(Message msg) {
        try {
            Address[] from = msg.getFrom();
            if (from == null || from.length == 0) return "";
            Address a = from[0];
            return a instanceof InternetAddress ia ? ia.getAddress() : a.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String safeSubject(Message msg) {
        try { String s = msg.getSubject(); return s == null ? "" : s; }
        catch (Exception e) { return ""; }
    }

    private static void closeSilently(AutoCloseable c) {
        if (c == null) return;
        try { c.close(); } catch (Exception ignored) {}
    }

    private static void closeSilently(Folder f) {
        if (f == null) return;
        try { if (f.isOpen()) f.close(false); } catch (Exception ignored) {}
    }

    private static void closeSilently(Store s) {
        if (s == null) return;
        try { if (s.isConnected()) s.close(); } catch (Exception ignored) {}
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
