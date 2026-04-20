package com.leadrush.enrichment.adapter.impl;

import com.leadrush.enrichment.adapter.EnrichmentProviderAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Mock enrichment adapter — for local testing without real API keys.
 *
 * Behavior:
 *   - If companyDomain is provided, generates a deterministic "firstName.lastName@domain" email
 *   - Always returns SUCCESS with confidence=75
 *   - No real HTTP calls, instant response
 *
 * Useful for:
 *   - Dev/demo environments
 *   - Integration tests
 *   - Showing the waterfall UI before real providers are connected
 */
@Component
@Slf4j
public class MockEnrichmentAdapter implements EnrichmentProviderAdapter {

    private static final String KEY = "MOCK";

    @Override public String providerKey() { return KEY; }
    @Override public String displayName() { return "Mock Provider (dev/testing)"; }
    @Override public boolean requiresApiKey() { return false; }

    @Override
    public EnrichmentResponse enrich(EnrichmentRequest request, String apiKey) {
        // Need at least first name + domain to generate something
        if (request.firstName() == null || request.firstName().isBlank()) {
            return EnrichmentResponse.notFound(null);
        }

        String domain = request.companyDomain();
        if ((domain == null || domain.isBlank()) && request.companyName() != null) {
            // Fall back to slugging the company name
            domain = request.companyName().toLowerCase().replaceAll("[^a-z0-9]+", "") + ".com";
        }
        if (domain == null || domain.isBlank()) {
            return EnrichmentResponse.notFound(null);
        }

        String firstPart = request.firstName().toLowerCase().replaceAll("[^a-z]+", "");
        String lastPart = request.lastName() != null
                ? request.lastName().toLowerCase().replaceAll("[^a-z]+", "")
                : "";
        String email = lastPart.isEmpty()
                ? firstPart + "@" + domain
                : firstPart + "." + lastPart + "@" + domain;

        log.debug("MockEnrichment generated email {} for {}", email, request);

        return EnrichmentResponse.success(
                email,
                null,                                // no phone
                request.companyName() != null ? "Contact at " + request.companyName() : null,
                null,                                // no LinkedIn
                75,                                  // mid confidence
                EnrichmentResponse.Confidence.LIKELY, // pretend mid-confidence for testing
                "{\"provider\":\"mock\",\"generated_email\":\"" + email + "\"}"
        );
    }
}
