package com.leadrush.email.controller;

import com.leadrush.common.ApiResponse;
import com.leadrush.email.dto.CreateEmailTemplateRequest;
import com.leadrush.email.dto.EmailTemplateResponse;
import com.leadrush.email.service.EmailTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/email-templates")
@RequiredArgsConstructor
public class EmailTemplateController {

    private final EmailTemplateService templateService;

    @GetMapping
    public ApiResponse<List<EmailTemplateResponse>> listTemplates() {
        return ApiResponse.success(templateService.listTemplates());
    }

    @GetMapping("/{id}")
    public ApiResponse<EmailTemplateResponse> getTemplate(@PathVariable UUID id) {
        return ApiResponse.success(templateService.getTemplate(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EmailTemplateResponse>> createTemplate(
            @Valid @RequestBody CreateEmailTemplateRequest request
    ) {
        EmailTemplateResponse template = templateService.createTemplate(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(template));
    }

    @PutMapping("/{id}")
    public ApiResponse<EmailTemplateResponse> updateTemplate(
            @PathVariable UUID id,
            @Valid @RequestBody CreateEmailTemplateRequest request
    ) {
        return ApiResponse.success(templateService.updateTemplate(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTemplate(@PathVariable UUID id) {
        templateService.deleteTemplate(id);
        return ApiResponse.success("Template deleted");
    }
}
