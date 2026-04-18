package com.leadrush.email.tracking;

import com.leadrush.leadscoring.entity.TriggerType;
import com.leadrush.leadscoring.service.LeadScoringService;
import com.leadrush.sequence.entity.EnrollmentStatus;
import com.leadrush.sequence.entity.SequenceEnrollment;
import com.leadrush.sequence.entity.SequenceStepExecution;
import com.leadrush.sequence.repository.SequenceEnrollmentRepository;
import com.leadrush.sequence.repository.SequenceStepExecutionRepository;
import com.leadrush.webhook.entity.WebhookEventType;
import com.leadrush.webhook.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Handles incoming tracking events from the public tracking endpoints.
 *
 * These methods run WITHOUT a tenant context (public endpoints have no JWT).
 * We look up the workspace_id from the execution record itself.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrackingEventService {

    private final SequenceStepExecutionRepository executionRepository;
    private final SequenceEnrollmentRepository enrollmentRepository;
    private final UnsubscribeRepository unsubscribeRepository;
    private final EmailLinkClickRepository clickRepository;
    private final LeadScoringService leadScoringService;
    private final WebhookService webhookService;

    /**
     * Record an email open event. Idempotent — only records the first open.
     */
    @Transactional
    public void recordOpen(UUID executionId) {
        executionRepository.findById(executionId).ifPresent(exec -> {
            if (exec.getOpenedAt() == null) {
                exec.setOpenedAt(LocalDateTime.now());
                executionRepository.save(exec);
                log.info("Email opened: execution={}", executionId);

                fireScoringForExecution(exec, TriggerType.EMAIL_OPENED, "Email opened");
                publishEmailEvent(exec, WebhookEventType.EMAIL_OPENED, java.util.Map.of());
            }
        });
    }

    /**
     * Record a link click. Always logs a new row (we care about all clicks),
     * but only sets first_clicked_at once on the execution.
     */
    @Transactional
    public void recordClick(UUID executionId, String clickedUrl, String userAgent, String ipAddress) {
        executionRepository.findById(executionId).ifPresent(exec -> {
            // Always log the click
            EmailLinkClick click = EmailLinkClick.builder()
                    .stepExecutionId(executionId)
                    .clickedUrl(clickedUrl)
                    .userAgent(userAgent)
                    .ipAddress(ipAddress)
                    .clickedAt(LocalDateTime.now())
                    .build();
            click.setWorkspaceId(exec.getWorkspaceId());
            clickRepository.save(click);

            // Record first click timestamp on the execution
            boolean firstClick = exec.getFirstClickedAt() == null;
            if (firstClick) {
                exec.setFirstClickedAt(LocalDateTime.now());
                // A click implies an open — if we missed the pixel, record open too
                if (exec.getOpenedAt() == null) {
                    exec.setOpenedAt(LocalDateTime.now());
                }
                executionRepository.save(exec);
            }
            log.info("Email clicked: execution={}, url={}", executionId, clickedUrl);

            // Score only on first click of this email (avoid point inflation on repeat clicks)
            if (firstClick) {
                fireScoringForExecution(exec, TriggerType.EMAIL_CLICKED, "Email clicked: " + clickedUrl);
                publishEmailEvent(exec, WebhookEventType.EMAIL_CLICKED, java.util.Map.of("url", clickedUrl));
            }
        });
    }

    /** Webhook publish helper — resolves contactId from enrollment and fires to the workspace. */
    private void publishEmailEvent(SequenceStepExecution exec, WebhookEventType type,
                                    java.util.Map<String, Object> extra) {
        enrollmentRepository.findById(exec.getEnrollmentId()).ifPresent(enrollment -> {
            java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("executionId", exec.getId());
            payload.put("enrollmentId", enrollment.getId());
            payload.put("sequenceId", enrollment.getSequence().getId());
            payload.put("sequenceName", enrollment.getSequence().getName());
            payload.put("contactId", enrollment.getContact().getId());
            payload.put("contactName", enrollment.getContact().getFullName());
            payload.putAll(extra);
            webhookService.publish(exec.getWorkspaceId(), type, payload);
        });
    }

    /** Resolve contact from execution → enrollment, then fire the scoring trigger. */
    private void fireScoringForExecution(SequenceStepExecution exec, TriggerType trigger, String reason) {
        enrollmentRepository.findById(exec.getEnrollmentId()).ifPresent(enrollment ->
            leadScoringService.fireTrigger(
                    exec.getWorkspaceId(),
                    trigger,
                    enrollment.getContact().getId(),
                    reason
            )
        );
    }

    /**
     * Record an unsubscribe event. Stops all active enrollments for this contact.
     */
    @Transactional
    public void recordUnsubscribe(UUID workspaceId, UUID contactId, Unsubscribe.Source source) {
        // Only record if not already unsubscribed
        if (unsubscribeRepository.existsByWorkspaceIdAndContactId(workspaceId, contactId)) {
            log.info("Contact {} already unsubscribed", contactId);
            return;
        }

        Unsubscribe unsub = Unsubscribe.builder()
                .contactId(contactId)
                .source(source)
                .build();
        unsub.setWorkspaceId(workspaceId);
        unsubscribeRepository.save(unsub);

        // Pause all ACTIVE enrollments for this contact
        enrollmentRepository.findByContactId(contactId).stream()
                .filter(e -> e.getStatus() == EnrollmentStatus.ACTIVE)
                .forEach(e -> {
                    e.setStatus(EnrollmentStatus.UNSUBSCRIBED);
                    enrollmentRepository.save(e);
                });

        log.info("Contact {} unsubscribed ({})", contactId, source);
    }
}
