package com.leadrush.landingpage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadrush.common.exception.BusinessException;
import com.leadrush.common.exception.ResourceNotFoundException;
import com.leadrush.contact.entity.Contact;
import com.leadrush.contact.entity.ContactEmail;
import com.leadrush.contact.entity.ContactPhone;
import com.leadrush.contact.entity.ContactSource;
import com.leadrush.contact.repository.ContactEmailRepository;
import com.leadrush.contact.repository.ContactRepository;
import com.leadrush.landingpage.dto.PublicSubmitRequest;
import com.leadrush.landingpage.entity.Form;
import com.leadrush.landingpage.entity.FormSubmission;
import com.leadrush.landingpage.repository.FormRepository;
import com.leadrush.landingpage.repository.FormSubmissionRepository;
import com.leadrush.leadscoring.entity.TriggerType;
import com.leadrush.leadscoring.service.LeadScoringService;
import com.leadrush.notification.entity.NotificationType;
import com.leadrush.notification.service.NotificationService;
import com.leadrush.sequence.entity.EnrollmentStatus;
import com.leadrush.webhook.entity.WebhookEventType;
import com.leadrush.webhook.service.WebhookService;
import com.leadrush.sequence.entity.Sequence;
import com.leadrush.sequence.entity.SequenceEnrollment;
import com.leadrush.sequence.entity.SequenceStatus;
import com.leadrush.sequence.repository.SequenceEnrollmentRepository;
import com.leadrush.sequence.repository.SequenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles PUBLIC form submissions.
 *
 * On submit:
 *   1. Validate form exists and is in the correct workspace
 *   2. Extract recognized fields (firstName, lastName, email, phone, companyName, title)
 *   3. Find existing contact by email OR create new one
 *   4. Save the raw submission for audit
 *   5. If the form has auto_enroll_sequence_id → enroll the contact
 *   6. Update landing page conversion counter
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FormSubmissionService {

    private final FormRepository formRepository;
    private final FormSubmissionRepository submissionRepository;
    private final ContactRepository contactRepository;
    private final ContactEmailRepository contactEmailRepository;
    private final SequenceRepository sequenceRepository;
    private final SequenceEnrollmentRepository enrollmentRepository;
    private final LandingPageService landingPageService;
    private final LeadScoringService leadScoringService;
    private final NotificationService notificationService;
    private final WebhookService webhookService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public record SubmitResult(
            String successMessage,
            String successRedirectUrl
    ) {}

    @Transactional
    public SubmitResult submit(PublicSubmitRequest request, String ipAddress, String userAgent) {
        // 1. Find the form (no tenant context — public endpoint)
        Form form = formRepository.findById(request.getFormId())
                .orElseThrow(() -> new ResourceNotFoundException("Form", request.getFormId()));

        UUID workspaceId = form.getWorkspaceId();

        // 2. Extract recognized fields from the submitted data
        Map<String, Object> data = request.getData();
        String email = asString(data.get("email"));
        String firstName = asString(data.get("firstName"));
        String lastName = asString(data.get("lastName"));
        String phone = asString(data.get("phone"));
        String companyName = asString(data.get("companyName"));
        String title = asString(data.get("title"));

        if (email == null || email.isBlank()) {
            throw new BusinessException("Email is required");
        }

        // 3. Find existing contact by email, or create new one
        boolean[] isNew = { false };
        Contact contact = findOrCreateContact(workspaceId, email, firstName, lastName, phone, title, isNew);

        // 4. Save the raw submission
        String dataJson;
        try {
            dataJson = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new BusinessException("Invalid submission data");
        }

        FormSubmission submission = FormSubmission.builder()
                .formId(form.getId())
                .landingPageId(request.getLandingPageId())
                .contactId(contact.getId())
                .data(dataJson)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .referrer(request.getReferrer())
                .utmSource(request.getUtmSource())
                .utmMedium(request.getUtmMedium())
                .utmCampaign(request.getUtmCampaign())
                .submittedAt(LocalDateTime.now())
                .build();
        submission.setWorkspaceId(workspaceId);
        submissionRepository.save(submission);

        // 5. Auto-enroll in sequence if configured
        if (form.getAutoEnrollSequenceId() != null) {
            autoEnroll(form.getAutoEnrollSequenceId(), contact, workspaceId);
        }

        // 6. Update page conversion counter
        if (request.getLandingPageId() != null) {
            landingPageService.recordConversion(request.getLandingPageId());
        }

        log.info("Form submission: form={} contact={} via={}",
                form.getId(), contact.getId(), request.getLandingPageId());

        // Fire scoring triggers — FORM_SUBMITTED always; CONTACT_CREATED for brand-new contacts
        if (isNew[0]) {
            leadScoringService.fireTrigger(workspaceId, TriggerType.CONTACT_CREATED, contact.getId(),
                    "Contact created via form: " + form.getName());
        }
        leadScoringService.fireTrigger(workspaceId, TriggerType.FORM_SUBMITTED, contact.getId(),
                "Form submitted: " + form.getName());

        // Broadcast to the workspace so any team member can pick up the lead
        notificationService.notifyWorkspace(
                workspaceId,
                NotificationType.FORM_SUBMITTED,
                "New form submission",
                (contact.getFullName() != null ? contact.getFullName() : email) + " submitted " + form.getName(),
                "/contacts/" + contact.getId(),
                Map.of("contactId", contact.getId(), "formId", form.getId())
        );

        webhookService.publish(workspaceId, WebhookEventType.FORM_SUBMITTED, Map.of(
                "contactId", contact.getId(),
                "contactName", contact.getFullName(),
                "contactEmail", email,
                "formId", form.getId(),
                "formName", form.getName(),
                "landingPageId", request.getLandingPageId() != null ? request.getLandingPageId() : "",
                "utmSource", request.getUtmSource() != null ? request.getUtmSource() : "",
                "utmMedium", request.getUtmMedium() != null ? request.getUtmMedium() : "",
                "utmCampaign", request.getUtmCampaign() != null ? request.getUtmCampaign() : ""
        ));

        return new SubmitResult(form.getSuccessMessage(), form.getSuccessRedirectUrl());
    }

    // ── Helpers ──

    private Contact findOrCreateContact(
            UUID workspaceId, String email, String firstName, String lastName,
            String phone, String title, boolean[] isNewOut
    ) {
        // Look for an existing email record in this workspace
        Optional<Contact> existing = contactEmailRepository.findAll().stream()
                .filter(ce -> email.equalsIgnoreCase(ce.getEmail()))
                .filter(ce -> Objects.equals(ce.getWorkspaceId(), workspaceId))
                .findFirst()
                .map(ContactEmail::getContact);
        if (existing.isPresent()) return existing.get();
        isNewOut[0] = true;
        return createContact(workspaceId, email, firstName, lastName, phone, title);
    }

    private Contact createContact(
            UUID workspaceId, String email, String firstName, String lastName,
            String phone, String title
    ) {
        Contact contact = Contact.builder()
                .firstName(firstName != null && !firstName.isBlank() ? firstName : "Form")
                .lastName(lastName)
                .title(title)
                .source(ContactSource.FORM)
                .build();
        contact.setWorkspaceId(workspaceId);

        ContactEmail emailEntity = ContactEmail.builder()
                .email(email)
                .emailType(ContactEmail.EmailType.WORK)
                .primary(true)
                .build();
        emailEntity.setWorkspaceId(workspaceId);
        contact.addEmail(emailEntity);

        if (phone != null && !phone.isBlank()) {
            ContactPhone phoneEntity = ContactPhone.builder()
                    .phone(phone)
                    .phoneType(ContactPhone.PhoneType.WORK)
                    .primary(true)
                    .build();
            phoneEntity.setWorkspaceId(workspaceId);
            contact.addPhone(phoneEntity);
        }

        return contactRepository.save(contact);
    }

    private void autoEnroll(UUID sequenceId, Contact contact, UUID workspaceId) {
        Optional<Sequence> sequenceOpt = sequenceRepository.findById(sequenceId);
        if (sequenceOpt.isEmpty()) return;

        Sequence sequence = sequenceOpt.get();
        if (!sequence.getWorkspaceId().equals(workspaceId)) return;
        if (sequence.getStatus() != SequenceStatus.ACTIVE) {
            log.info("Skipping auto-enroll — sequence {} is not ACTIVE", sequenceId);
            return;
        }
        if (sequence.getSteps().isEmpty()) return;
        if (sequence.getDefaultMailbox() == null) {
            log.info("Skipping auto-enroll — sequence {} has no default mailbox", sequenceId);
            return;
        }

        // Skip if contact is already enrolled in this sequence
        if (enrollmentRepository.findBySequenceIdAndContactId(sequenceId, contact.getId()).isPresent()) {
            return;
        }

        SequenceEnrollment enrollment = SequenceEnrollment.builder()
                .sequence(sequence)
                .contact(contact)
                .mailbox(sequence.getDefaultMailbox())
                .currentStepIndex(0)
                .nextExecutionAt(LocalDateTime.now())
                .status(EnrollmentStatus.ACTIVE)
                .build();
        enrollment.setWorkspaceId(workspaceId);
        enrollmentRepository.save(enrollment);

        sequence.setTotalEnrolled(sequence.getTotalEnrolled() + 1);
        log.info("Form auto-enrolled contact {} in sequence {}", contact.getId(), sequenceId);
    }

    private String asString(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }
}
