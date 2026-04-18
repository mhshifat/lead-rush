package com.leadrush.apikey.controller;

import com.leadrush.apikey.dto.ApiKeyResponse;
import com.leadrush.apikey.dto.CreateApiKeyRequest;
import com.leadrush.apikey.service.ApiKeyService;
import com.leadrush.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @GetMapping
    public ApiResponse<List<ApiKeyResponse>> list() {
        return ApiResponse.success(apiKeyService.list());
    }

    /** Plaintext is present in the response ONLY here — never shown again. */
    @PostMapping
    public ApiResponse<ApiKeyResponse> create(@Valid @RequestBody CreateApiKeyRequest request) {
        return ApiResponse.success(apiKeyService.create(request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> revoke(@PathVariable UUID id) {
        apiKeyService.revoke(id);
        return ApiResponse.success(null);
    }
}
