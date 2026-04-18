package com.leadrush.enrichment.controller;

import com.leadrush.common.ApiResponse;
import com.leadrush.enrichment.dto.EnrichmentProviderRequest;
import com.leadrush.enrichment.dto.EnrichmentProviderResponse;
import com.leadrush.enrichment.dto.EnrichmentResultResponse;
import com.leadrush.enrichment.service.EnrichmentService;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/enrichment")
@RequiredArgsConstructor
public class EnrichmentController {

    private final EnrichmentService enrichmentService;

    // ── Provider config ──

    /**
     * GET /api/v1/enrichment/providers — list all providers (auto-creates rows for unseen adapters).
     */
    @GetMapping("/providers")
    public ApiResponse<List<EnrichmentProviderResponse>> listProviders() {
        return ApiResponse.success(enrichmentService.listProviders());
    }

    /**
     * PUT /api/v1/enrichment/providers — update a provider's config (key, enabled, priority).
     */
    @PutMapping("/providers")
    public ApiResponse<EnrichmentProviderResponse> updateProvider(
            @Valid @RequestBody EnrichmentProviderRequest request
    ) {
        return ApiResponse.success(enrichmentService.updateProvider(request));
    }

    // ── Enrichment actions ──

    /**
     * POST /api/v1/enrichment/contacts/{id} — run the waterfall for a single contact.
     */
    @PostMapping("/contacts/{id}")
    public ApiResponse<EnrichmentResultResponse> enrichContact(@PathVariable UUID id) {
        EnrichmentResultResponse result = enrichmentService.enrichContact(id);
        return ApiResponse.success(result);
    }

    /**
     * POST /api/v1/enrichment/bulk — bulk enrich a list of contacts.
     */
    @PostMapping("/bulk")
    public ApiResponse<EnrichmentService.BulkEnrichmentResult> enrichBulk(
            @RequestBody BulkRequest request
    ) {
        return ApiResponse.success(enrichmentService.enrichBulk(request.getContactIds()));
    }

    /**
     * GET /api/v1/enrichment/contacts/{id}/results — all past enrichment attempts for this contact.
     */
    @GetMapping("/contacts/{id}/results")
    public ApiResponse<List<EnrichmentResultResponse>> listResults(@PathVariable UUID id) {
        return ApiResponse.success(enrichmentService.listResultsForContact(id));
    }

    @Data
    public static class BulkRequest {
        private List<UUID> contactIds;
    }
}
