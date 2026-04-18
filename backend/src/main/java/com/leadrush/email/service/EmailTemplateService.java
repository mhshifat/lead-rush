package com.leadrush.email.service;

import com.leadrush.common.exception.ResourceNotFoundException;
import com.leadrush.contact.entity.Contact;
import com.leadrush.email.dto.*;
import com.leadrush.email.entity.EmailTemplate;
import com.leadrush.email.repository.EmailTemplateRepository;
import com.leadrush.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// Supports {{firstName}}, {{lastName}}, {{fullName}}, {{companyName}}, {{title}}, {{email}}.
// Unresolved placeholders are left in place so the sender can spot missing data.
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailTemplateService {

    private final EmailTemplateRepository templateRepository;

    @Transactional(readOnly = true)
    public List<EmailTemplateResponse> listTemplates() {
        UUID workspaceId = TenantContext.getWorkspaceId();
        return templateRepository.findByWorkspaceId(workspaceId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public EmailTemplateResponse getTemplate(UUID id) {
        return toResponse(findTemplate(id));
    }

    @Transactional
    public EmailTemplateResponse createTemplate(CreateEmailTemplateRequest request) {
        UUID workspaceId = TenantContext.getWorkspaceId();

        EmailTemplate template = EmailTemplate.builder()
                .name(request.getName())
                .subject(request.getSubject())
                .bodyHtml(request.getBodyHtml())
                .bodyText(request.getBodyText())
                .build();
        template.setWorkspaceId(workspaceId);

        template = templateRepository.save(template);
        log.info("Email template created: {} (id: {})", template.getName(), template.getId());
        return toResponse(template);
    }

    @Transactional
    public EmailTemplateResponse updateTemplate(UUID id, CreateEmailTemplateRequest request) {
        EmailTemplate template = findTemplate(id);
        template.setName(request.getName());
        template.setSubject(request.getSubject());
        template.setBodyHtml(request.getBodyHtml());
        template.setBodyText(request.getBodyText());
        template = templateRepository.save(template);
        return toResponse(template);
    }

    @Transactional
    public void deleteTemplate(UUID id) {
        EmailTemplate template = findTemplate(id);
        templateRepository.delete(template);
        log.info("Email template deleted: {} (id: {})", template.getName(), id);
    }

    public String replaceVariables(String template, Contact contact) {
        if (template == null) return null;

        Map<String, String> variables = Map.of(
                "{{firstName}}", nullToEmpty(contact.getFirstName()),
                "{{lastName}}", nullToEmpty(contact.getLastName()),
                "{{fullName}}", nullToEmpty(contact.getFullName()),
                "{{companyName}}", contact.getCompany() != null
                        ? nullToEmpty(contact.getCompany().getName()) : "",
                "{{title}}", nullToEmpty(contact.getTitle()),
                "{{email}}", nullToEmpty(contact.getPrimaryEmail())
        );

        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                result = result.replace(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** Loader used by other services that need the raw template entity. */
    public EmailTemplate findTemplate(UUID id) {
        UUID workspaceId = TenantContext.getWorkspaceId();
        return templateRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("EmailTemplate", id));
    }

    private EmailTemplateResponse toResponse(EmailTemplate t) {
        return EmailTemplateResponse.builder()
                .id(t.getId())
                .name(t.getName())
                .subject(t.getSubject())
                .bodyHtml(t.getBodyHtml())
                .bodyText(t.getBodyText())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }
}
