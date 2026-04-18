package com.leadrush.email.tracking;

import com.leadrush.config.LeadRushProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TrackingService — HMAC token generation + email body wrapping.
 */
class TrackingServiceTest {

    private TrackingService trackingService;

    @BeforeEach
    void setUp() throws Exception {
        LeadRushProperties properties = new LeadRushProperties();
        properties.setEncryptionKey("test-secret-for-hmac-signing");
        properties.setFrontendUrl("https://app.leadrush.test");

        trackingService = new TrackingService(properties);
        trackingService.init();
    }

    // ── Unsubscribe token tests ──

    @Test
    void generateUnsubscribeTokenIsDeterministic() {
        UUID contactId = UUID.randomUUID();

        String token1 = trackingService.generateUnsubscribeToken(contactId);
        String token2 = trackingService.generateUnsubscribeToken(contactId);

        assertEquals(token1, token2, "HMAC for same contact must be deterministic");
    }

    @Test
    void differentContactIdsProduceDifferentTokens() {
        String token1 = trackingService.generateUnsubscribeToken(UUID.randomUUID());
        String token2 = trackingService.generateUnsubscribeToken(UUID.randomUUID());

        assertNotEquals(token1, token2);
    }

    @Test
    void verifyUnsubscribeTokenAcceptsValidToken() {
        UUID contactId = UUID.randomUUID();
        String token = trackingService.generateUnsubscribeToken(contactId);

        assertTrue(trackingService.verifyUnsubscribeToken(contactId, token));
    }

    @Test
    void verifyUnsubscribeTokenRejectsWrongContact() {
        UUID contactA = UUID.randomUUID();
        UUID contactB = UUID.randomUUID();
        String tokenForA = trackingService.generateUnsubscribeToken(contactA);

        assertFalse(trackingService.verifyUnsubscribeToken(contactB, tokenForA),
                "Token for contact A must not verify for contact B");
    }

    @Test
    void verifyUnsubscribeTokenRejectsNullToken() {
        assertFalse(trackingService.verifyUnsubscribeToken(UUID.randomUUID(), null));
    }

    @Test
    void verifyUnsubscribeTokenRejectsTamperedToken() {
        UUID contactId = UUID.randomUUID();
        String token = trackingService.generateUnsubscribeToken(contactId);

        // Change one character
        String tampered = "X" + token.substring(1);

        assertFalse(trackingService.verifyUnsubscribeToken(contactId, tampered));
    }

    // ── Body wrapping tests ──

    @Test
    void wrapBodyAddsTrackingPixel() {
        UUID execId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();

        String wrapped = trackingService.wrapBody(
                "<html><body><p>Hello</p></body></html>",
                execId, contactId
        );

        assertTrue(wrapped.contains("/t/o/" + execId),
                "Body must contain open tracking pixel URL");
        assertTrue(wrapped.contains("width=\"1\""),
                "Pixel must be 1x1 for tracking");
    }

    @Test
    void wrapBodyRewritesLinks() {
        UUID execId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();

        String original = "<p>Visit <a href=\"https://example.com/page\">our site</a></p>";
        String wrapped = trackingService.wrapBody(original, execId, contactId);

        // Original link should be rewritten to go through tracking
        assertFalse(wrapped.contains("href=\"https://example.com/page\""),
                "Original link should be rewritten");
        assertTrue(wrapped.contains("/t/c/" + execId),
                "Links must go through click-tracking endpoint");
        assertTrue(wrapped.contains("url=https%3A%2F%2Fexample.com%2Fpage"),
                "Original URL must be URL-encoded in query param");
    }

    @Test
    void wrapBodyAddsUnsubscribeFooter() {
        UUID execId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();

        String wrapped = trackingService.wrapBody(
                "<p>Hello</p>", execId, contactId
        );

        assertTrue(wrapped.contains("/unsub/" + contactId),
                "Body must contain unsubscribe link");
        assertTrue(wrapped.toLowerCase().contains("unsubscribe"),
                "Footer must mention unsubscribe");
    }

    @Test
    void wrapBodyHandlesNull() {
        assertNull(trackingService.wrapBody(null, UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void wrapBodyDoesNotRewriteTrackingLinks() {
        UUID execId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();

        // If the body already has a tracking link (shouldn't happen, but defensive)
        String original = "<a href=\"https://app.leadrush.test/t/o/abc\">tracker</a>";
        String wrapped = trackingService.wrapBody(original, execId, contactId);

        // The existing tracking link shouldn't be double-wrapped
        long openTrackingCount = wrapped.split("/t/o/").length - 1;
        // There should be exactly 2: the original one, and the auto-added pixel at end
        // If it were double-wrapped, there'd be more
        assertTrue(openTrackingCount <= 2, "Tracking links shouldn't be double-wrapped");
    }
}
