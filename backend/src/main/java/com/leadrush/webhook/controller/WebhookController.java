package com.leadrush.webhook.controller;

import com.leadrush.common.ApiResponse;
import com.leadrush.webhook.dto.WebhookDeliveryResponse;
import com.leadrush.webhook.dto.WebhookEndpointRequest;
import com.leadrush.webhook.dto.WebhookEndpointResponse;
import com.leadrush.webhook.entity.WebhookEventType;
import com.leadrush.webhook.service.WebhookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;

    /** Catalog of available event topics — powers the multi-select on the frontend. */
    @GetMapping("/event-types")
    public ApiResponse<List<String>> eventTypes() {
        return ApiResponse.success(WebhookEventType.allTopics());
    }

    @GetMapping
    public ApiResponse<List<WebhookEndpointResponse>> list() {
        return ApiResponse.success(webhookService.list());
    }

    @PostMapping
    public ApiResponse<WebhookEndpointResponse> create(@Valid @RequestBody WebhookEndpointRequest request) {
        return ApiResponse.success(webhookService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<WebhookEndpointResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody WebhookEndpointRequest request
    ) {
        return ApiResponse.success(webhookService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        webhookService.delete(id);
        return ApiResponse.success(null);
    }

    /** Rotate the signing secret — returns the new plaintext once. */
    @PostMapping("/{id}/rotate-secret")
    public ApiResponse<WebhookEndpointResponse> rotateSecret(@PathVariable UUID id) {
        return ApiResponse.success(webhookService.rotateSecret(id));
    }

    /** Fire a `test.ping` event to verify the endpoint is reachable. */
    @PostMapping("/{id}/test")
    public ApiResponse<Void> test(@PathVariable UUID id) {
        webhookService.sendTest(id);
        return ApiResponse.success(null);
    }

    /** Paginated delivery log for one endpoint. */
    @GetMapping("/{id}/deliveries")
    public ApiResponse<Page<WebhookDeliveryResponse>> deliveries(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(webhookService.deliveries(id, page, size));
    }
}
