package com.leadrush.webhook.service;

import com.leadrush.webhook.entity.WebhookDelivery;
import com.leadrush.webhook.entity.WebhookEndpoint;
import com.leadrush.webhook.repository.WebhookDeliveryRepository;
import com.leadrush.webhook.repository.WebhookEndpointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Scheduled delivery of queued webhook payloads.
 * Signature header: X-LeadRush-Signature: t=<unix>,v1=HMAC-SHA256(secret, "<t>.<body>").
 * Backoff: 1,5,30,120,480 minutes, then ABANDONED after 5 failed attempts.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebhookDeliveryJob {

    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 5;
    /** Index into BACKOFF_MINUTES is the NEXT attempt count (after current attempt). */
    private static final int[] BACKOFF_MINUTES = { 1, 5, 30, 120, 480 };
    private static final int REQUEST_TIMEOUT_MS = 10_000;

    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookEndpointRepository endpointRepository;
    // @Lazy self-proxy so @Transactional fires when runBatch() calls deliverOne().
    @Autowired
    @Lazy
    private WebhookDeliveryJob self;

    private final RestClient httpClient = RestClient.builder().build();

    @Scheduled(fixedDelay = 10_000, initialDelay = 5_000)
    public void runBatch() {
        List<WebhookDelivery> due;
        try {
            due = deliveryRepository.findDue(LocalDateTime.now(), PageRequest.of(0, BATCH_SIZE));
        } catch (Exception e) {
            log.warn("Webhook batch query failed: {}", e.getMessage());
            return;
        }
        if (due.isEmpty()) return;

        log.debug("Processing {} due webhook deliveries", due.size());
        for (WebhookDelivery delivery : due) {
            try {
                self.deliverOne(delivery.getId());
            } catch (Exception e) {
                // deliverOne already logs; this catch is belt-and-suspenders
                log.warn("Delivery {} threw: {}", delivery.getId(), e.getMessage());
            }
        }
    }

    // Per-delivery transaction so one failure can't roll back siblings in the batch.
    @Transactional
    public void deliverOne(java.util.UUID deliveryId) {
        Optional<WebhookDelivery> maybe = deliveryRepository.findById(deliveryId);
        if (maybe.isEmpty()) return;

        WebhookDelivery delivery = maybe.get();
        // Skip if another node grabbed it already or status changed
        if (delivery.getStatus() != WebhookDelivery.Status.PENDING
                && delivery.getStatus() != WebhookDelivery.Status.FAILED) {
            return;
        }

        WebhookEndpoint endpoint = endpointRepository.findById(delivery.getEndpointId()).orElse(null);
        if (endpoint == null || !endpoint.isEnabled()) {
            // Endpoint was deleted/disabled after enqueue — abandon
            markAbandoned(delivery, "Endpoint not available");
            return;
        }

        int attemptNumber = delivery.getAttemptCount() + 1;
        long timestamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        String body = delivery.getPayload();
        String signature = sign(endpoint.getSecret(), timestamp, body);

        delivery.setStatus(WebhookDelivery.Status.IN_PROGRESS);
        delivery.setAttemptCount(attemptNumber);
        delivery.setLastAttemptAt(LocalDateTime.now());
        deliveryRepository.save(delivery);

        try {
            int statusCode = httpClient.post()
                    .uri(endpoint.getUrl())
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .header("X-LeadRush-Signature", "t=" + timestamp + ",v1=" + signature)
                    .header("X-LeadRush-Event", delivery.getEventType())
                    .header("X-LeadRush-Event-Id", delivery.getEventId().toString())
                    .header("X-LeadRush-Delivery", delivery.getId().toString())
                    .header("User-Agent", "LeadRush-Webhook/1.0")
                    .body(body)
                    .exchange((req, res) -> res.getStatusCode().value());

            delivery.setLastStatusCode(statusCode);

            if (statusCode >= 200 && statusCode < 300) {
                markSucceeded(delivery, endpoint);
            } else {
                markFailed(delivery, endpoint, attemptNumber,
                        "Non-2xx response: " + statusCode);
            }
        } catch (Exception e) {
            delivery.setLastStatusCode(null);
            markFailed(delivery, endpoint, attemptNumber,
                    shortError(e));
        }
    }

    // ── State transitions ──

    private void markSucceeded(WebhookDelivery delivery, WebhookEndpoint endpoint) {
        delivery.setStatus(WebhookDelivery.Status.SUCCEEDED);
        delivery.setDeliveredAt(LocalDateTime.now());
        delivery.setLastError(null);
        deliveryRepository.save(delivery);

        endpoint.setConsecutiveFailures(0);
        endpoint.setLastSuccessAt(LocalDateTime.now());
        endpoint.setDisabledReason(null);
        endpointRepository.save(endpoint);
    }

    private void markFailed(WebhookDelivery delivery, WebhookEndpoint endpoint,
                             int attemptNumber, String error) {
        delivery.setLastError(error);

        if (attemptNumber >= MAX_ATTEMPTS) {
            delivery.setStatus(WebhookDelivery.Status.ABANDONED);
            delivery.setAbandonedAt(LocalDateTime.now());
        } else {
            int backoffIdx = Math.min(attemptNumber - 1, BACKOFF_MINUTES.length - 1);
            delivery.setStatus(WebhookDelivery.Status.FAILED);
            delivery.setNextAttemptAt(LocalDateTime.now().plusMinutes(BACKOFF_MINUTES[backoffIdx]));
        }
        deliveryRepository.save(delivery);

        // Endpoint-level failure tracking
        endpoint.setLastFailureAt(LocalDateTime.now());
        endpoint.setConsecutiveFailures(endpoint.getConsecutiveFailures() + 1);
        if (endpoint.getConsecutiveFailures() >= WebhookService.FAILURE_AUTO_DISABLE_THRESHOLD) {
            endpoint.setEnabled(false);
            endpoint.setDisabledReason("Auto-disabled after "
                    + endpoint.getConsecutiveFailures() + " consecutive failures");
        }
        endpointRepository.save(endpoint);

        log.info("Webhook delivery {} failed (attempt {}/{}): {}",
                delivery.getId(), attemptNumber, MAX_ATTEMPTS, error);
    }

    private void markAbandoned(WebhookDelivery delivery, String reason) {
        delivery.setStatus(WebhookDelivery.Status.ABANDONED);
        delivery.setAbandonedAt(LocalDateTime.now());
        delivery.setLastError(reason);
        deliveryRepository.save(delivery);
    }

    // ── Helpers ──

    private static String sign(String secret, long timestamp, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String payload = timestamp + "." + body;
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    private static String shortError(Throwable t) {
        String msg = t.getMessage();
        if (msg == null) msg = t.getClass().getSimpleName();
        return msg.length() > 500 ? msg.substring(0, 500) : msg;
    }
}
