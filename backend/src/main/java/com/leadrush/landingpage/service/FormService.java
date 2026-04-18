package com.leadrush.landingpage.service;

import com.leadrush.common.exception.ResourceNotFoundException;
import com.leadrush.landingpage.dto.CreateFormRequest;
import com.leadrush.landingpage.dto.FormResponse;
import com.leadrush.landingpage.entity.Form;
import com.leadrush.landingpage.repository.FormRepository;
import com.leadrush.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FormService {

    private final FormRepository formRepository;

    @Transactional(readOnly = true)
    public List<FormResponse> listForms() {
        UUID workspaceId = TenantContext.getWorkspaceId();
        return formRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public FormResponse getForm(UUID id) {
        return toResponse(findForm(id));
    }

    @Transactional
    public FormResponse createForm(CreateFormRequest request) {
        UUID workspaceId = TenantContext.getWorkspaceId();

        Form form = Form.builder()
                .name(request.getName())
                .description(request.getDescription())
                .fields(request.getFields() != null ? request.getFields() : defaultFields())
                .successRedirectUrl(request.getSuccessRedirectUrl())
                .successMessage(request.getSuccessMessage() != null
                        ? request.getSuccessMessage()
                        : "Thank you! We'll be in touch.")
                .autoEnrollSequenceId(request.getAutoEnrollSequenceId())
                .build();
        form.setWorkspaceId(workspaceId);

        form = formRepository.save(form);
        log.info("Form created: {} (id: {})", form.getName(), form.getId());
        return toResponse(form);
    }

    @Transactional
    public FormResponse updateForm(UUID id, CreateFormRequest request) {
        Form form = findForm(id);
        form.setName(request.getName());
        form.setDescription(request.getDescription());
        if (request.getFields() != null) form.setFields(request.getFields());
        form.setSuccessRedirectUrl(request.getSuccessRedirectUrl());
        if (request.getSuccessMessage() != null) form.setSuccessMessage(request.getSuccessMessage());
        form.setAutoEnrollSequenceId(request.getAutoEnrollSequenceId());
        return toResponse(formRepository.save(form));
    }

    @Transactional
    public void deleteForm(UUID id) {
        formRepository.delete(findForm(id));
    }

    // ── Helpers ──

    public Form findForm(UUID id) {
        UUID workspaceId = TenantContext.getWorkspaceId();
        return formRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Form", id));
    }

    private String defaultFields() {
        return """
                [
                  {"key":"firstName","label":"First Name","type":"text","required":true},
                  {"key":"email","label":"Email","type":"email","required":true}
                ]
                """;
    }

    private FormResponse toResponse(Form f) {
        return FormResponse.builder()
                .id(f.getId())
                .name(f.getName())
                .description(f.getDescription())
                .fields(f.getFields())
                .successRedirectUrl(f.getSuccessRedirectUrl())
                .successMessage(f.getSuccessMessage())
                .autoEnrollSequenceId(f.getAutoEnrollSequenceId())
                .createdAt(f.getCreatedAt())
                .updatedAt(f.getUpdatedAt())
                .build();
    }
}
