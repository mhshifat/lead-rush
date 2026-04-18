package com.leadrush.email.tracking;

import com.leadrush.contact.repository.ContactRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Public tracking endpoints — hit by email clients (open pixel) and
 * user browsers (click redirect, unsubscribe).
 *
 * These endpoints MUST be public (permitAll) in Spring Security because
 * the requests don't carry a JWT — they come from email clients or anonymous users.
 *
 * SECURITY:
 *   - /t/o/{executionId} and /t/c/{executionId} use UUIDs (unguessable)
 *   - /unsub/{contactId} requires an HMAC token that we verify
 *   - We NEVER return any private data from these endpoints
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class TrackingController {

    private final TrackingEventService trackingEventService;
    private final TrackingService trackingService;
    private final ContactRepository contactRepository;

    /** 1x1 transparent GIF89a — classic tracking pixel (43 bytes). */
    private static final byte[] TRANSPARENT_GIF = Base64.getDecoder().decode(
            "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7"
    );

    /**
     * GET /t/o/{executionId}
     * Open tracking pixel — returns a 1x1 transparent GIF + logs the open.
     */
    @GetMapping(value = "/t/o/{executionId}", produces = MediaType.IMAGE_GIF_VALUE)
    public ResponseEntity<byte[]> trackOpen(@PathVariable UUID executionId) {
        try {
            trackingEventService.recordOpen(executionId);
        } catch (Exception e) {
            log.warn("Failed to record open for {}: {}", executionId, e.getMessage());
        }

        // Always return the pixel, even if logging failed — don't show broken images
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .contentType(MediaType.IMAGE_GIF)
                .body(TRANSPARENT_GIF);
    }

    /**
     * GET /t/c/{executionId}?url=<encoded-url>
     * Click redirect — logs the click then 302 redirects to the original URL.
     */
    @GetMapping("/t/c/{executionId}")
    public ResponseEntity<Void> trackClick(
            @PathVariable UUID executionId,
            @RequestParam("url") String url,
            HttpServletRequest request
    ) {
        try {
            trackingEventService.recordClick(
                    executionId,
                    url,
                    request.getHeader("User-Agent"),
                    getClientIp(request)
            );
        } catch (Exception e) {
            log.warn("Failed to record click for {}: {}", executionId, e.getMessage());
        }

        try {
            return ResponseEntity.status(302).location(URI.create(url)).build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid redirect URL: {}", url);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * GET /unsub/{contactId}?token=<hmac>
     * Browser-initiated unsubscribe — shows a confirmation page.
     */
    @GetMapping(value = "/unsub/{contactId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> unsubscribePage(
            @PathVariable UUID contactId,
            @RequestParam("token") String token
    ) {
        if (!trackingService.verifyUnsubscribeToken(contactId, token)) {
            return ResponseEntity.status(400).body(unsubscribeHtml(
                    "Invalid unsubscribe link",
                    "This link appears to be invalid or tampered with."
            ));
        }

        Optional<UUID> workspaceId = findWorkspaceIdForContact(contactId);
        if (workspaceId.isEmpty()) {
            return ResponseEntity.status(404).body(unsubscribeHtml(
                    "Contact not found",
                    "We couldn't find your subscription."
            ));
        }

        trackingEventService.recordUnsubscribe(
                workspaceId.get(), contactId, Unsubscribe.Source.LINK_CLICK
        );

        return ResponseEntity.ok(unsubscribeHtml(
                "You've been unsubscribed",
                "You will no longer receive emails from this sender."
        ));
    }

    /**
     * POST /unsub/{contactId}?token=<hmac>
     * RFC 8058 one-click unsubscribe — Gmail/Outlook call this via their native
     * Unsubscribe button. Must return 200 OK (no redirect).
     */
    @PostMapping("/unsub/{contactId}")
    public ResponseEntity<String> oneClickUnsubscribe(
            @PathVariable UUID contactId,
            @RequestParam("token") String token
    ) {
        if (!trackingService.verifyUnsubscribeToken(contactId, token)) {
            return ResponseEntity.status(400).build();
        }

        Optional<UUID> workspaceId = findWorkspaceIdForContact(contactId);
        if (workspaceId.isEmpty()) {
            return ResponseEntity.status(404).build();
        }

        trackingEventService.recordUnsubscribe(
                workspaceId.get(), contactId, Unsubscribe.Source.LIST_UNSUBSCRIBE
        );

        return ResponseEntity.ok("Unsubscribed");
    }

    // ── Helpers ──

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private Optional<UUID> findWorkspaceIdForContact(UUID contactId) {
        return contactRepository.findById(contactId).map(c -> c.getWorkspaceId());
    }

    private String unsubscribeHtml(String title, String message) {
        return """
                <!DOCTYPE html>
                <html><head>
                <title>%s</title>
                <style>
                    body { font-family: -apple-system, sans-serif; max-width: 500px; margin: 100px auto; text-align: center; color: #333; }
                    h1 { color: #111; }
                    p { color: #666; }
                </style>
                </head><body>
                <h1>%s</h1>
                <p>%s</p>
                </body></html>
                """.formatted(title, title, message);
    }
}
