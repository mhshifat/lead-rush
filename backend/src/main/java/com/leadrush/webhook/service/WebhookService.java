package com.leadrush.webhook.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadrush.common.exception.BusinessException;
import com.leadrush.common.exception.ConflictException;
import com.leadrush.common.exception.ResourceNotFoundException;
import com.leadrush.security.TenantContext;
import com.leadrush.webhook.dto.WebhookDeliveryResponse;
import com.leadrush.webhook.dto.WebhookEndpointRequest;
import com.leadrush.webhook.dto.WebhookEndpointResponse;
import com.leadrush.webhook.entity.WebhookDelivery;
import com.leadrush.webhook.entity.WebhookEndpoint;
import com.leadrush.webhook.entity.WebhookEventType;
import com.leadrush.webhook.repository.WebhookDeliveryRepository;
import com.leadrush.webhook.repository.WebhookEndpointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Webhook endpoint CRUD + publish. Delivery loop lives in {@link WebhookDeliveryJob}.
 * publish() never throws — webhook failures must not break the code that fired the event.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private static final SecureRandom RNG = new SecureRandom();

    /** Auto-disable an endpoint after this many consecutive failed deliveries. */
    public static final int FAILURE_AUTO_DISABLE_THRESHOLD = 20;

    private final WebhookEndpointRepository endpointRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final ObjectMapper objectMapper;

    // ── Management ──

    @Transactional(readOnly = true)
    public List<WebhookEndpointResponse> list() {
        UUID wsId = TenantContext.getWorkspaceId();
        return endpointRepository.findByWorkspaceIdOrderByCreatedAtDesc(wsId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public WebhookEndpointResponse create(WebhookEndpointRequest request) {
        UUID wsId = TenantContext.getWorkspaceId();

        validateEvents(request.getEvents());

        String secret = generateSecret();
        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .url(request.getUrl().trim())
                .description(trimOrNull(request.getDescription()))
                .secret(secret)
                .events(joinEvents(request.getEvents()))
                .enabled(request.getEnabled() == null || request.getEnabled())
                .build();
        endpoint.setWorkspaceId(wsId);
        endpoint = endpointRepository.save(endpoint);

        WebhookEndpointResponse response = toResponse(endpoint);
        response.setSecret(secret); // ONLY surfaced here
        return response;
    }

    @Transactional
    public WebhookEndpointResponse update(UUID id, WebhookEndpointRequest request) {
        UUID wsId = TenantContext.getWorkspaceId();
        WebhookEndpoint endpoint = endpointRepository.findByIdAndWorkspaceId(id, wsId)
                .orElseThrow(() -> new ResourceNotFoundException("WebhookEndpoint", id));

        if (request.getUrl() != null) endpoint.setUrl(request.getUrl().trim());
        if (request.getDescription() != null) endpoint.setDescription(trimOrNull(request.getDescription()));
        if (request.getEvents() != null) {
            validateEvents(request.getEvents());
            endpoint.setEvents(joinEvents(request.getEvents()));
        }
        if (request.getEnabled() != null) {
            endpoint.setEnabled(request.getEnabled());
            if (request.getEnabled()) {
                // Re-enabling resets the failure counter so the endpoint gets a fair retry
                endpoint.setConsecutiveFailures(0);
                endpoint.setDisabledReason(null);
            }
        }
        endpointRepository.save(endpoint);
        return toResponse(endpoint);
    }

    @Transactional
    public void delete(UUID id) {
        UUID wsId = TenantContext.getWorkspaceId();
        WebhookEndpoint endpoint = endpointRepository.findByIdAndWorkspaceId(id, wsId)
                .orElseThrow(() -> new ResourceNotFoundException("WebhookEndpoint", id));
        endpointRepository.delete(endpoint);
    }

    /** Rotate the secret — returns the new plaintext exactly once. */
    @Transactional
    public WebhookEndpointResponse rotateSecret(UUID id) {
        UUID wsId = TenantContext.getWorkspaceId();
        WebhookEndpoint endpoint = endpointRepository.findByIdAndWorkspaceId(id, wsId)
                .orElseThrow(() -> new ResourceNotFoundException("WebhookEndpoint", id));
        String secret = generateSecret();
        endpoint.setSecret(secret);
        endpointRepository.save(endpoint);
        WebhookEndpointResponse response = toResponse(endpoint);
        response.setSecret(secret);
        return response;
    }

    /** Fire a synthetic "test.ping" event to let the user verify their endpoint works. */
    @Transactional
    public void sendTest(UUID id) {
        UUID wsId = TenantContext.getWorkspaceId();
        WebhookEndpoint endpoint = endpointRepository.findByIdAndWorkspaceId(id, wsId)
                .orElseThrow(() -> new ResourceNotFoundException("WebhookEndpoint", id));
        if (!endpoint.isEnabled()) {
            throw new BusinessException("Enable the endpoint before sending a test");
        }
        Map<String, Object> data = Map.of("message", "Test event from Lead Rush");
        enqueueDelivery(endpoint, WebhookEventType.TEST, data, UUID.randomUUID());
    }

    @Transactional(readOnly = true)
    public Page<WebhookDeliveryResponse> deliveries(UUID endpointId, int page, int size) {
        UUID wsId = TenantContext.getWorkspaceId();
        // Tenant check: endpoint must belong to current workspace
        endpointRepository.findByIdAndWorkspaceId(endpointId, wsId)
                .orElseThrow(() -> new ResourceNotFoundException("WebhookEndpoint", endpointId));

        return deliveryRepository
                .findByEndpointIdOrderByCreatedAtDesc(endpointId, PageRequest.of(page, size))
                .map(this::toDeliveryResponse);
    }

    // ── Publish (called from other services) ──

    /** Publish using the current tenant context. Safe no-op if there's no context. */
    public void publish(WebhookEventType eventType, Map<String, Object> data) {
        UUID wsId = TenantContext.getWorkspaceId();
        if (wsId == null) return;
        publish(wsId, eventType, data);
    }

    /** Publish to a specific workspace — use from public endpoints where tenant isn't set. */
    @Transactional
    public void publish(UUID workspaceId, WebhookEventType eventType, Map<String, Object> data) {
        try {
            List<WebhookEndpoint> endpoints = endpointRepository
                    .findByWorkspaceIdAndEnabledTrue(workspaceId);
            if (endpoints.isEmpty()) return;

            UUID eventId = UUID.randomUUID();
            for (WebhookEndpoint endpoint : endpoints) {
                if (endpoint.subscribesTo(eventType)) {
                    enqueueDelivery(endpoint, eventType, data, eventId);
                }
            }
        } catch (Exception e) {
            log.warn("Webhook publish failed (event={}, workspace={}): {}",
                    eventType, workspaceId, e.getMessage());
        }
    }

    // ── Internals ──

    private void enqueueDelivery(WebhookEndpoint endpoint, WebhookEventType eventType,
                                  Map<String, Object> data, UUID eventId) {
        String payload = buildPayload(eventId, eventType, data);

        WebhookDelivery delivery = WebhookDelivery.builder()
                .endpointId(endpoint.getId())
                .eventType(eventType.topic())
                .eventId(eventId)
                .payload(payload)
                .status(WebhookDelivery.Status.PENDING)
                .nextAttemptAt(LocalDateTime.now())
                .build();
        delivery.setWorkspaceId(endpoint.getWorkspaceId());
        deliveryRepository.save(delivery);
    }

    private String buildPayload(UUID eventId, WebhookEventType eventType, Map<String, Object> data) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", eventId.toString());
        envelope.put("type", eventType.topic());
        envelope.put("createdAt", LocalDateTime.now().toString());
        envelope.put("data", data == null ? Map.of() : data);
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            // Should never happen with Map<String,Object> of simple values
            throw new IllegalStateException("Failed to serialize webhook payload", e);
        }
    }

    private void validateEvents(List<String> events) {
        if (events == null) return;
        List<String> valid = WebhookEventType.allTopics();
        for (String topic : events) {
            String trimmed = topic.trim();
            if (trimmed.equals("*")) continue;
            if (!valid.contains(trimmed)) {
                throw new ConflictException("Unknown event type: " + trimmed);
            }
        }
    }

    private static String joinEvents(List<String> events) {
        if (events == null || events.isEmpty()) return "";
        return String.join(",", events.stream().map(String::trim).toList());
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String generateSecret() {
        byte[] bytes = new byte[32];
        RNG.nextBytes(bytes);
        return "whsec_" + HexFormat.of().formatHex(bytes);
    }

    private WebhookEndpointResponse toResponse(WebhookEndpoint endpoint) {
        return WebhookEndpointResponse.builder()
                .id(endpoint.getId())
                .url(endpoint.getUrl())
                .description(endpoint.getDescription())
                .events(endpoint.subscribedTopics())
                .enabled(endpoint.isEnabled())
                .consecutiveFailures(endpoint.getConsecutiveFailures())
                .disabledReason(endpoint.getDisabledReason())
                .lastSuccessAt(endpoint.getLastSuccessAt())
                .lastFailureAt(endpoint.getLastFailureAt())
                .createdAt(endpoint.getCreatedAt())
                .updatedAt(endpoint.getUpdatedAt())
                .build();
    }

    private WebhookDeliveryResponse toDeliveryResponse(WebhookDelivery d) {
        return WebhookDeliveryResponse.builder()
                .id(d.getId())
                .endpointId(d.getEndpointId())
                .eventType(d.getEventType())
                .eventId(d.getEventId())
                .payload(d.getPayload())
                .status(d.getStatus().name())
                .attemptCount(d.getAttemptCount())
                .lastStatusCode(d.getLastStatusCode())
                .lastError(d.getLastError())
                .nextAttemptAt(d.getNextAttemptAt())
                .lastAttemptAt(d.getLastAttemptAt())
                .deliveredAt(d.getDeliveredAt())
                .createdAt(d.getCreatedAt())
                .build();
    }
}
