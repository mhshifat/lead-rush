package com.leadrush.sequence.service;

import com.leadrush.common.exception.BusinessException;
import com.leadrush.common.exception.ResourceNotFoundException;
import com.leadrush.contact.entity.Contact;
import com.leadrush.contact.repository.ContactRepository;
import com.leadrush.email.adapter.EmailSenderAdapter;
import com.leadrush.email.entity.EmailSendLog;
import com.leadrush.email.entity.EmailTemplate;
import com.leadrush.email.entity.Mailbox;
import com.leadrush.email.repository.EmailSendLogRepository;
import com.leadrush.email.repository.MailboxRepository;
import com.leadrush.email.service.EmailTemplateService;
import com.leadrush.email.service.MailboxService;
import com.leadrush.email.tracking.TrackingService;
import com.leadrush.email.tracking.UnsubscribeRepository;
import com.leadrush.leadscoring.entity.TriggerType;
import com.leadrush.leadscoring.service.LeadScoringService;
import com.leadrush.notification.entity.NotificationType;
import com.leadrush.notification.service.NotificationService;
import com.leadrush.security.TenantContext;
import com.leadrush.webhook.entity.WebhookEventType;
import com.leadrush.webhook.service.WebhookService;
import com.leadrush.sequence.dto.EnrollmentResponse;
import com.leadrush.sequence.entity.*;
import com.leadrush.sequence.repository.SequenceEnrollmentRepository;
import com.leadrush.sequence.repository.SequenceStepExecutionRepository;
import com.leadrush.task.entity.Task;
import com.leadrush.task.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Enrollment service — manages contacts in sequences + executes individual steps.
 *
 * ENROLL flow:
 *   1. Validate sequence is ACTIVE and has steps
 *   2. Check contact isn't already enrolled
 *   3. Create enrollment with currentStepIndex = 0, next_execution_at = NOW
 *   4. Increment sequence.totalEnrolled counter
 *
 * EXECUTE_NEXT_STEP flow (called by SequenceExecutionJob):
 *   1. Get the step at currentStepIndex
 *   2. If EMAIL: render template, send email, log execution
 *   3. If DELAY: skip (delay is handled by next_execution_at calculation)
 *   4. Advance currentStepIndex
 *   5. If more steps: calculate next_execution_at based on next step's delay_days
 *   6. If no more steps: mark COMPLETED
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnrollmentService {

    private final SequenceEnrollmentRepository enrollmentRepository;
    private final SequenceStepExecutionRepository executionRepository;
    private final ContactRepository contactRepository;
    private final MailboxRepository mailboxRepository;
    private final EmailSendLogRepository sendLogRepository;
    private final EmailTemplateService emailTemplateService;
    private final MailboxService mailboxService;
    private final EmailSenderAdapter emailSender;
    private final SequenceService sequenceService;
    private final TrackingService trackingService;
    private final UnsubscribeRepository unsubscribeRepository;
    private final TaskService taskService;
    private final LeadScoringService leadScoringService;
    private final NotificationService notificationService;
    private final WebhookService webhookService;

    // ── ENROLL ──

    @Transactional
    public EnrollmentResponse enrollContact(UUID sequenceId, UUID contactId, UUID mailboxId) {
        UUID workspaceId = TenantContext.getWorkspaceId();

        Sequence sequence = sequenceService.findSequence(sequenceId);

        if (sequence.getStatus() != SequenceStatus.ACTIVE) {
            throw new BusinessException("Sequence must be ACTIVE to enroll contacts");
        }
        if (sequence.getSteps().isEmpty()) {
            throw new BusinessException("Sequence has no steps");
        }

        Contact contact = contactRepository.findByIdAndWorkspaceId(contactId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Contact", contactId));

        // Check not already enrolled
        enrollmentRepository.findBySequenceIdAndContactId(sequenceId, contactId)
                .ifPresent(e -> {
                    throw new BusinessException("Contact is already enrolled in this sequence");
                });

        // Determine mailbox: explicit > sequence default > error
        Mailbox mailbox;
        if (mailboxId != null) {
            mailbox = mailboxRepository.findByIdAndWorkspaceId(mailboxId, workspaceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Mailbox", mailboxId));
        } else if (sequence.getDefaultMailbox() != null) {
            mailbox = sequence.getDefaultMailbox();
        } else {
            throw new BusinessException("No mailbox specified and sequence has no default mailbox");
        }

        SequenceEnrollment enrollment = SequenceEnrollment.builder()
                .sequence(sequence)
                .contact(contact)
                .mailbox(mailbox)
                .currentStepIndex(0)
                .nextExecutionAt(LocalDateTime.now())    // first step runs immediately
                .status(EnrollmentStatus.ACTIVE)
                .build();
        enrollment.setWorkspaceId(workspaceId);

        enrollment = enrollmentRepository.save(enrollment);

        // Update sequence counters
        sequence.setTotalEnrolled(sequence.getTotalEnrolled() + 1);

        log.info("Contact {} enrolled in sequence {}", contact.getFullName(), sequence.getName());

        leadScoringService.fireTrigger(TriggerType.ENROLLED, contact.getId(),
                "Enrolled in sequence: " + sequence.getName());

        webhookService.publish(WebhookEventType.ENROLLMENT_CREATED, java.util.Map.of(
                "enrollmentId", enrollment.getId(),
                "sequenceId", sequence.getId(),
                "sequenceName", sequence.getName(),
                "contactId", contact.getId(),
                "contactName", contact.getFullName(),
                "contactEmail", contact.getPrimaryEmail() != null ? contact.getPrimaryEmail() : ""
        ));

        return toResponse(enrollment);
    }

    @Transactional(readOnly = true)
    public List<EnrollmentResponse> listEnrollmentsForContact(UUID contactId) {
        return enrollmentRepository.findByContactId(contactId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EnrollmentResponse> listEnrollmentsForSequence(UUID sequenceId) {
        return enrollmentRepository.findBySequenceId(sequenceId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public EnrollmentResponse pauseEnrollment(UUID id) {
        SequenceEnrollment enrollment = findEnrollment(id);
        enrollment.setStatus(EnrollmentStatus.PAUSED);
        enrollmentRepository.save(enrollment);
        return toResponse(enrollment);
    }

    @Transactional
    public EnrollmentResponse resumeEnrollment(UUID id) {
        SequenceEnrollment enrollment = findEnrollment(id);
        if (enrollment.getStatus() != EnrollmentStatus.PAUSED) {
            throw new BusinessException("Only paused enrollments can be resumed");
        }
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        enrollment.setNextExecutionAt(LocalDateTime.now());
        enrollmentRepository.save(enrollment);
        return toResponse(enrollment);
    }

    // ── EXECUTE (called by SequenceExecutionJob) ──

    /**
     * Execute the next step for an enrollment.
     * Called by the scheduler for each due enrollment.
     *
     * IMPORTANT: This method is @Transactional so the whole state transition
     * (send email + update enrollment + log execution) is atomic.
     */
    @Transactional
    public void executeNextStep(UUID enrollmentId) {
        SequenceEnrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElse(null);

        if (enrollment == null || enrollment.getStatus() != EnrollmentStatus.ACTIVE) {
            return;     // already paused/completed/etc.
        }

        Sequence sequence = enrollment.getSequence();
        List<SequenceStep> steps = sequence.getSteps();

        if (enrollment.getCurrentStepIndex() >= steps.size()) {
            completeEnrollment(enrollment);
            return;
        }

        SequenceStep currentStep = steps.get(enrollment.getCurrentStepIndex());

        // Execute based on step type
        try {
            switch (currentStep.getStepType()) {
                case EMAIL -> executeEmailStep(enrollment, currentStep);
                case DELAY -> executeDelayStep(enrollment, currentStep);
                case CALL -> executeTaskStep(enrollment, currentStep, Task.TaskType.CALL);
                case TASK -> executeTaskStep(enrollment, currentStep, Task.TaskType.MANUAL);
                case LINKEDIN_MESSAGE -> executeTaskStep(enrollment, currentStep, Task.TaskType.LINKEDIN_MESSAGE);
                case LINKEDIN_CONNECT -> executeTaskStep(enrollment, currentStep, Task.TaskType.LINKEDIN_CONNECT);
            }

            // Advance to next step
            advanceToNextStep(enrollment, steps);

        } catch (Exception e) {
            log.error("Failed to execute step for enrollment {}: {}", enrollmentId, e.getMessage(), e);
            recordExecution(enrollment, currentStep, SequenceStepExecution.ExecutionStatus.FAILED, null, e.getMessage());
            // Keep enrollment ACTIVE so it can be retried next tick (or user can investigate)
        }
    }

    private void executeEmailStep(SequenceEnrollment enrollment, SequenceStep step) {
        Contact contact = enrollment.getContact();
        Mailbox mailbox = enrollment.getMailbox();

        String recipientEmail = contact.getPrimaryEmail();
        if (recipientEmail == null) {
            enrollment.setStatus(EnrollmentStatus.FAILED);
            recordExecution(enrollment, step, SequenceStepExecution.ExecutionStatus.FAILED,
                    null, "Contact has no email address");
            return;
        }

        // Check unsubscribe status — never send to unsubscribed contacts
        if (unsubscribeRepository.existsByWorkspaceIdAndContactId(
                enrollment.getWorkspaceId(), contact.getId())) {
            log.info("Skipping email — contact {} is unsubscribed", contact.getId());
            enrollment.setStatus(EnrollmentStatus.UNSUBSCRIBED);
            recordExecution(enrollment, step, SequenceStepExecution.ExecutionStatus.SKIPPED,
                    null, "Contact is unsubscribed");
            notificationService.notifyWorkspace(
                    enrollment.getWorkspaceId(),
                    NotificationType.ENROLLMENT_UNSUBSCRIBED,
                    "Contact unsubscribed",
                    contact.getFullName() + " unsubscribed from " + enrollment.getSequence().getName(),
                    "/contacts/" + contact.getId(),
                    java.util.Map.of(
                            "contactId", contact.getId(),
                            "sequenceId", enrollment.getSequence().getId()
                    )
            );
            webhookService.publish(enrollment.getWorkspaceId(),
                    WebhookEventType.ENROLLMENT_UNSUBSCRIBED, java.util.Map.of(
                    "enrollmentId", enrollment.getId(),
                    "sequenceId", enrollment.getSequence().getId(),
                    "sequenceName", enrollment.getSequence().getName(),
                    "contactId", contact.getId(),
                    "contactName", contact.getFullName()
            ));
            return;
        }

        // Conditional branching — skip this step if a prior email in the same enrollment
        // was opened / clicked. Useful for drip sequences that stop pestering engaged leads.
        String skipReason = evaluateSkipCondition(enrollment, step);
        if (skipReason != null) {
            log.info("Skipping email step {} for enrollment {}: {}", step.getStepOrder(), enrollment.getId(), skipReason);
            recordExecution(enrollment, step, SequenceStepExecution.ExecutionStatus.SKIPPED, null, skipReason);
            notificationService.notifyWorkspace(
                    enrollment.getWorkspaceId(),
                    NotificationType.SEQUENCE_STEP_SKIPPED,
                    "Step skipped — " + enrollment.getSequence().getName(),
                    contact.getFullName() + " · " + skipReason,
                    "/sequences/" + enrollment.getSequence().getId(),
                    java.util.Map.of(
                            "contactId", contact.getId(),
                            "sequenceId", enrollment.getSequence().getId(),
                            "stepOrder", step.getStepOrder()
                    )
            );
            return;
        }

        // Resolve subject & body from template + overrides
        EmailTemplate template = step.getEmailTemplate();
        String subject = step.getSubjectOverride() != null
                ? step.getSubjectOverride()
                : (template != null ? template.getSubject() : "");
        String bodyHtml = step.getBodyHtmlOverride() != null
                ? step.getBodyHtmlOverride()
                : (template != null ? template.getBodyHtml() : "");
        String bodyText = template != null ? template.getBodyText() : null;

        // Apply variable replacement
        subject = emailTemplateService.replaceVariables(subject, contact);
        bodyHtml = emailTemplateService.replaceVariables(bodyHtml, contact);
        bodyText = emailTemplateService.replaceVariables(bodyText, contact);

        // Check mailbox daily limit
        if (!mailbox.canSendToday()) {
            log.warn("Mailbox {} hit daily limit; deferring step for enrollment {}",
                    mailbox.getEmail(), enrollment.getId());
            enrollment.setNextExecutionAt(LocalDateTime.now().plusHours(1));
            return;     // retry in an hour
        }

        // STEP 1: Create step_execution record FIRST, so we have its ID for tracking pixel/clicks
        SequenceStepExecution execution = SequenceStepExecution.builder()
                .enrollmentId(enrollment.getId())
                .sequenceStepId(step.getId())
                .status(SequenceStepExecution.ExecutionStatus.PENDING)
                .build();
        execution.setWorkspaceId(enrollment.getWorkspaceId());
        execution = executionRepository.save(execution);

        // STEP 2: Inject tracking pixel + rewrite links + add unsubscribe footer
        String trackedBodyHtml = trackingService.wrapBody(bodyHtml, execution.getId(), contact.getId());

        // STEP 3: Build List-Unsubscribe header (RFC 8058) — enables Gmail/Outlook's native unsubscribe button
        String unsubscribeUrl = trackingService.buildUnsubscribeUrl(contact.getId());
        var headers = java.util.Map.of(
                "List-Unsubscribe", "<" + unsubscribeUrl + ">",
                "List-Unsubscribe-Post", "List-Unsubscribe=One-Click"
        );

        // STEP 4: Send via adapter
        var credentials = mailboxService.getDecryptedCredentials(mailbox);
        var sendRequest = new EmailSenderAdapter.SendRequest(
                credentials,
                mailbox.getEmail(),
                mailbox.getName(),
                recipientEmail,
                subject,
                trackedBodyHtml,
                bodyText,
                headers
        );

        EmailSenderAdapter.SendResult result = emailSender.send(sendRequest);

        // STEP 5: Update execution with result
        execution.setStatus(result.success()
                ? SequenceStepExecution.ExecutionStatus.SENT
                : SequenceStepExecution.ExecutionStatus.FAILED);
        execution.setMessageId(result.messageId());
        execution.setErrorMessage(result.errorMessage());
        if (result.success()) execution.setSentAt(LocalDateTime.now());
        executionRepository.save(execution);

        // STEP 6: Log send at the raw email level too
        EmailSendLog sendLog = EmailSendLog.builder()
                .mailboxId(mailbox.getId())
                .contactId(contact.getId())
                .stepExecutionId(execution.getId())
                .toEmail(recipientEmail)
                .fromEmail(mailbox.getEmail())
                .subject(subject)
                .bodyHtml(trackedBodyHtml)
                .messageId(result.messageId())
                .status(result.success() ? EmailSendLog.SendStatus.SENT : EmailSendLog.SendStatus.FAILED)
                .errorMessage(result.errorMessage())
                .sentAt(result.success() ? LocalDateTime.now() : null)
                .build();
        sendLog.setWorkspaceId(enrollment.getWorkspaceId());
        sendLogRepository.save(sendLog);

        // Update mailbox counter
        if (result.success()) {
            mailbox.recordSend();
            mailboxRepository.save(mailbox);
        }

        log.info("Email step executed: {} → {} ({})",
                mailbox.getEmail(), recipientEmail, result.success() ? "SENT" : "FAILED");
    }

    private void executeDelayStep(SequenceEnrollment enrollment, SequenceStep step) {
        // DELAY doesn't do anything at execution time — the wait is handled by
        // setting next_execution_at when advancing to the next step.
        recordExecution(enrollment, step, SequenceStepExecution.ExecutionStatus.SKIPPED, null, null);
    }

    /**
     * Execute a CALL / TASK / LINKEDIN_MESSAGE / LINKEDIN_CONNECT step.
     * Creates a Task for the user to perform manually, then advances the sequence.
     */
    private void executeTaskStep(SequenceEnrollment enrollment, SequenceStep step, Task.TaskType type) {
        taskService.createFromSequenceStep(
                enrollment.getWorkspaceId(),
                enrollment.getContact().getId(),
                enrollment.getSequence().getId(),
                enrollment.getId(),
                step.getTaskDescription(),
                type
        );
        recordExecution(enrollment, step, SequenceStepExecution.ExecutionStatus.SENT, null, null);
        log.info("Task step executed: created {} task for contact {}", type, enrollment.getContact().getFullName());
    }

    private void advanceToNextStep(SequenceEnrollment enrollment, List<SequenceStep> steps) {
        int newIndex = enrollment.getCurrentStepIndex() + 1;
        enrollment.setCurrentStepIndex(newIndex);

        if (newIndex >= steps.size()) {
            completeEnrollment(enrollment);
        } else {
            // Calculate next_execution_at based on NEXT step's delay
            SequenceStep nextStep = steps.get(newIndex);
            enrollment.setNextExecutionAt(LocalDateTime.now().plusDays(nextStep.getDelayDays()));
        }
        enrollmentRepository.save(enrollment);
    }

    /**
     * Check the skip_if_* flags against prior executions in this same enrollment.
     * Returns the reason string if the step should be skipped, or null to proceed.
     *
     * We look at executions that were SENT and check whether any was opened/clicked.
     * Scope is the current enrollment only — so restarting someone in a new enrollment
     * resets the branch state.
     */
    private String evaluateSkipCondition(SequenceEnrollment enrollment, SequenceStep step) {
        if (!step.isSkipIfPreviousOpened() && !step.isSkipIfPreviousClicked()) {
            return null;
        }

        List<SequenceStepExecution> prior = executionRepository
                .findByEnrollmentIdOrderByCreatedAtAsc(enrollment.getId());

        if (step.isSkipIfPreviousClicked()) {
            boolean anyClicked = prior.stream().anyMatch(e -> e.getFirstClickedAt() != null);
            if (anyClicked) return "Contact already clicked an earlier email in this sequence";
        }
        if (step.isSkipIfPreviousOpened()) {
            boolean anyOpened = prior.stream().anyMatch(e -> e.getOpenedAt() != null);
            if (anyOpened) return "Contact already opened an earlier email in this sequence";
        }
        return null;
    }

    private void completeEnrollment(SequenceEnrollment enrollment) {
        enrollment.setStatus(EnrollmentStatus.COMPLETED);
        enrollment.setCompletedAt(LocalDateTime.now());
        enrollment.setNextExecutionAt(null);

        Sequence sequence = enrollment.getSequence();
        sequence.setTotalCompleted(sequence.getTotalCompleted() + 1);

        enrollmentRepository.save(enrollment);

        notificationService.notifyWorkspace(
                enrollment.getWorkspaceId(),
                com.leadrush.notification.entity.NotificationType.ENROLLMENT_COMPLETED,
                "Sequence completed",
                enrollment.getContact().getFullName() + " finished " + sequence.getName(),
                "/contacts/" + enrollment.getContact().getId(),
                java.util.Map.of(
                        "contactId", enrollment.getContact().getId(),
                        "sequenceId", sequence.getId()
                )
        );

        webhookService.publish(enrollment.getWorkspaceId(),
                WebhookEventType.ENROLLMENT_COMPLETED, java.util.Map.of(
                "enrollmentId", enrollment.getId(),
                "sequenceId", sequence.getId(),
                "sequenceName", sequence.getName(),
                "contactId", enrollment.getContact().getId(),
                "contactName", enrollment.getContact().getFullName(),
                "stepsExecuted", enrollment.getCurrentStepIndex()
        ));
    }

    private void recordExecution(
            SequenceEnrollment enrollment,
            SequenceStep step,
            SequenceStepExecution.ExecutionStatus status,
            String messageId,
            String errorMessage
    ) {
        SequenceStepExecution exec = SequenceStepExecution.builder()
                .enrollmentId(enrollment.getId())
                .sequenceStepId(step.getId())
                .status(status)
                .messageId(messageId)
                .errorMessage(errorMessage)
                .sentAt(status == SequenceStepExecution.ExecutionStatus.SENT ? LocalDateTime.now() : null)
                .build();
        exec.setWorkspaceId(enrollment.getWorkspaceId());
        executionRepository.save(exec);
    }

    // ── Helpers ──

    private SequenceEnrollment findEnrollment(UUID id) {
        UUID workspaceId = TenantContext.getWorkspaceId();
        return enrollmentRepository.findById(id)
                .filter(e -> e.getWorkspaceId().equals(workspaceId))
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment", id));
    }

    private EnrollmentResponse toResponse(SequenceEnrollment e) {
        return EnrollmentResponse.builder()
                .id(e.getId())
                .sequenceId(e.getSequence().getId())
                .sequenceName(e.getSequence().getName())
                .contactId(e.getContact().getId())
                .contactFullName(e.getContact().getFullName())
                .contactEmail(e.getContact().getPrimaryEmail())
                .mailboxId(e.getMailbox() != null ? e.getMailbox().getId() : null)
                .mailboxEmail(e.getMailbox() != null ? e.getMailbox().getEmail() : null)
                .currentStepIndex(e.getCurrentStepIndex())
                .nextExecutionAt(e.getNextExecutionAt())
                .status(e.getStatus().name())
                .enrolledAt(e.getEnrolledAt())
                .completedAt(e.getCompletedAt())
                .build();
    }
}
