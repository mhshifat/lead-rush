package com.leadrush.sequence.service;

import com.leadrush.common.exception.BusinessException;
import com.leadrush.common.exception.ResourceNotFoundException;
import com.leadrush.email.entity.EmailTemplate;
import com.leadrush.email.entity.Mailbox;
import com.leadrush.email.repository.EmailTemplateRepository;
import com.leadrush.email.repository.MailboxRepository;
import com.leadrush.security.TenantContext;
import com.leadrush.sequence.dto.*;
import com.leadrush.sequence.entity.*;
import com.leadrush.sequence.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Sequence service — CRUD + step management + activation.
 *
 * Lifecycle:
 *   - Create sequence (status = DRAFT)
 *   - Add steps
 *   - Activate (status = ACTIVE → ready for enrollments)
 *   - Pause/unpause anytime
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SequenceService {

    private final SequenceRepository sequenceRepository;
    private final SequenceStepRepository stepRepository;
    private final MailboxRepository mailboxRepository;
    private final EmailTemplateRepository templateRepository;

    @Transactional(readOnly = true)
    public List<SequenceResponse> listSequences() {
        UUID workspaceId = TenantContext.getWorkspaceId();
        return sequenceRepository.findByWorkspaceId(workspaceId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SequenceResponse getSequence(UUID id) {
        return toResponse(findSequence(id));
    }

    @Transactional
    public SequenceResponse createSequence(CreateSequenceRequest request) {
        UUID workspaceId = TenantContext.getWorkspaceId();

        Sequence sequence = Sequence.builder()
                .name(request.getName())
                .description(request.getDescription())
                .status(SequenceStatus.DRAFT)
                .build();
        sequence.setWorkspaceId(workspaceId);

        // Link default mailbox if provided
        if (request.getDefaultMailboxId() != null) {
            Mailbox mailbox = mailboxRepository.findByIdAndWorkspaceId(
                    request.getDefaultMailboxId(), workspaceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Mailbox", request.getDefaultMailboxId()));
            sequence.setDefaultMailbox(mailbox);
        }

        sequence = sequenceRepository.save(sequence);
        log.info("Sequence created: {} (id: {})", sequence.getName(), sequence.getId());
        return toResponse(sequence);
    }

    @Transactional
    public SequenceResponse activateSequence(UUID id) {
        Sequence sequence = findSequence(id);
        if (sequence.getSteps().isEmpty()) {
            throw new BusinessException("Cannot activate a sequence with no steps");
        }
        sequence.setStatus(SequenceStatus.ACTIVE);
        sequenceRepository.save(sequence);
        return toResponse(sequence);
    }

    @Transactional
    public SequenceResponse pauseSequence(UUID id) {
        Sequence sequence = findSequence(id);
        sequence.setStatus(SequenceStatus.PAUSED);
        sequenceRepository.save(sequence);
        return toResponse(sequence);
    }

    @Transactional
    public void deleteSequence(UUID id) {
        Sequence sequence = findSequence(id);
        sequenceRepository.delete(sequence);
        log.info("Sequence deleted: {} (id: {})", sequence.getName(), id);
    }

    // ── Step management ──

    @Transactional
    public SequenceResponse addStep(UUID sequenceId, CreateStepRequest request) {
        Sequence sequence = findSequence(sequenceId);
        UUID workspaceId = TenantContext.getWorkspaceId();

        // Determine next step order
        int nextOrder = sequence.getSteps().stream()
                .mapToInt(SequenceStep::getStepOrder)
                .max()
                .orElse(0) + 1;

        SequenceStep step = SequenceStep.builder()
                .stepOrder(nextOrder)
                .stepType(StepType.valueOf(request.getStepType()))
                .delayDays(request.getDelayDays() != null ? request.getDelayDays() : 0)
                .subjectOverride(request.getSubjectOverride())
                .bodyHtmlOverride(request.getBodyHtmlOverride())
                .taskDescription(request.getTaskDescription())
                .skipIfPreviousOpened(Boolean.TRUE.equals(request.getSkipIfPreviousOpened()))
                .skipIfPreviousClicked(Boolean.TRUE.equals(request.getSkipIfPreviousClicked()))
                .build();
        step.setWorkspaceId(workspaceId);

        // Link email template if provided
        if (request.getEmailTemplateId() != null) {
            EmailTemplate template = templateRepository.findByIdAndWorkspaceId(
                    request.getEmailTemplateId(), workspaceId)
                    .orElseThrow(() -> new ResourceNotFoundException("EmailTemplate", request.getEmailTemplateId()));
            step.setEmailTemplate(template);
        }

        sequence.addStep(step);
        sequenceRepository.save(sequence);

        return toResponse(sequence);
    }

    @Transactional
    public SequenceResponse deleteStep(UUID sequenceId, UUID stepId) {
        Sequence sequence = findSequence(sequenceId);
        sequence.getSteps().removeIf(s -> s.getId().equals(stepId));
        sequenceRepository.save(sequence);
        return toResponse(sequence);
    }

    // ── Helpers ──

    /** Public entity loader (used by EnrollmentService). */
    public Sequence findSequence(UUID id) {
        UUID workspaceId = TenantContext.getWorkspaceId();
        return sequenceRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Sequence", id));
    }

    private SequenceResponse toResponse(Sequence sequence) {
        List<SequenceResponse.StepResponse> stepDtos = sequence.getSteps().stream()
                .map(this::toStepResponse)
                .toList();

        return SequenceResponse.builder()
                .id(sequence.getId())
                .name(sequence.getName())
                .description(sequence.getDescription())
                .status(sequence.getStatus().name())
                .defaultMailboxId(sequence.getDefaultMailbox() != null
                        ? sequence.getDefaultMailbox().getId() : null)
                .defaultMailboxEmail(sequence.getDefaultMailbox() != null
                        ? sequence.getDefaultMailbox().getEmail() : null)
                .totalEnrolled(sequence.getTotalEnrolled())
                .totalCompleted(sequence.getTotalCompleted())
                .totalReplied(sequence.getTotalReplied())
                .steps(stepDtos)
                .createdAt(sequence.getCreatedAt())
                .updatedAt(sequence.getUpdatedAt())
                .build();
    }

    private SequenceResponse.StepResponse toStepResponse(SequenceStep step) {
        return SequenceResponse.StepResponse.builder()
                .id(step.getId())
                .stepOrder(step.getStepOrder())
                .stepType(step.getStepType().name())
                .delayDays(step.getDelayDays())
                .emailTemplateId(step.getEmailTemplate() != null ? step.getEmailTemplate().getId() : null)
                .emailTemplateName(step.getEmailTemplate() != null ? step.getEmailTemplate().getName() : null)
                .subjectOverride(step.getSubjectOverride())
                .bodyHtmlOverride(step.getBodyHtmlOverride())
                .taskDescription(step.getTaskDescription())
                .skipIfPreviousOpened(step.isSkipIfPreviousOpened())
                .skipIfPreviousClicked(step.isSkipIfPreviousClicked())
                .build();
    }
}
