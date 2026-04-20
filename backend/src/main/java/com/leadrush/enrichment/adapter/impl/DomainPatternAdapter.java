package com.leadrush.enrichment.adapter.impl;

import com.leadrush.enrichment.adapter.EnrichmentProviderAdapter;
import com.leadrush.enrichment.service.DomainPatternCacheService;
import com.leadrush.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// Tier 1 of the waterfall: free, instant.
// If another adapter has already discovered the email pattern for this domain in this
// workspace, we construct the email directly — no external call, no rate limit, no cost.
@Component
@RequiredArgsConstructor
@Slf4j
public class DomainPatternAdapter implements EnrichmentProviderAdapter {

    private static final String KEY = "PATTERN_CACHE";

    private final DomainPatternCacheService cacheService;

    @Override public String providerKey() { return KEY; }
    @Override public String displayName() { return "Domain pattern cache"; }
    @Override public boolean requiresApiKey() { return false; }

    @Override
    public EnrichmentResponse enrich(EnrichmentRequest request, String apiKey) {
        if (request.companyDomain() == null || request.companyDomain().isBlank()) {
            return EnrichmentResponse.notFound(null);
        }
        if ((request.firstName() == null || request.firstName().isBlank())
                && (request.lastName() == null || request.lastName().isBlank())) {
            return EnrichmentResponse.notFound(null);
        }

        var tenantId = TenantContext.getWorkspaceId();
        var cached = cacheService.lookup(tenantId, request.companyDomain()).orElse(null);
        if (cached == null) return EnrichmentResponse.notFound(null);
        if (cached.isCatchAll()) {
            // We know this domain is catch-all — every address "looks" valid but none are trustworthy.
            return EnrichmentResponse.notFound("catch-all domain");
        }

        String email = cacheService.constructEmail(
                tenantId, request.firstName(), request.lastName(), request.companyDomain());
        if (email == null) return EnrichmentResponse.notFound(null);

        log.debug("Pattern cache hit: {} ({}) → {}", request.companyDomain(), cached.getPatternType(), email);
        // Confidence maps loosely to Hunter's 0-100 scale — higher = more re-confirmations.
        int confidence = Math.min(100, 50 + cached.getConfidence() * 10);
        // Cached-pattern emails graduate from GUESSED → LIKELY as we see the same
        // pattern work for more contacts at this domain. Fresh domains stay at GUESSED.
        var level = cached.getConfidence() >= 2
                ? EnrichmentResponse.Confidence.LIKELY
                : EnrichmentResponse.Confidence.GUESSED;
        return EnrichmentResponse.success(email, null, null, null, confidence, level, null);
    }
}
