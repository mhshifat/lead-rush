package com.leadrush.landingpage.controller;

import com.leadrush.common.ApiResponse;
import com.leadrush.landingpage.dto.CreateFormRequest;
import com.leadrush.landingpage.dto.FormResponse;
import com.leadrush.landingpage.service.FormService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/forms")
@RequiredArgsConstructor
public class FormController {

    private final FormService formService;

    @GetMapping
    public ApiResponse<List<FormResponse>> listForms() {
        return ApiResponse.success(formService.listForms());
    }

    @GetMapping("/{id}")
    public ApiResponse<FormResponse> getForm(@PathVariable UUID id) {
        return ApiResponse.success(formService.getForm(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<FormResponse>> createForm(@Valid @RequestBody CreateFormRequest request) {
        FormResponse form = formService.createForm(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(form));
    }

    @PutMapping("/{id}")
    public ApiResponse<FormResponse> updateForm(@PathVariable UUID id, @Valid @RequestBody CreateFormRequest request) {
        return ApiResponse.success(formService.updateForm(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteForm(@PathVariable UUID id) {
        formService.deleteForm(id);
        return ApiResponse.success("Form deleted");
    }
}
