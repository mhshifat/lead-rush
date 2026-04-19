package com.leadrush.enrichment.adapter.impl;

import com.leadrush.enrichment.adapter.EnrichmentProviderAdapter;
import com.leadrush.enrichment.entity.CompanyCrawlPerson;
import com.leadrush.enrichment.repository.CompanyCrawlPersonRepository;
import com.leadrush.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

// Reads from the background-crawl person cache populated by CompanyCrawlWorkerJob.
// Near-zero latency (single indexed query) and free. Sits between the pattern cache
// and the live-crawl adapters so warm caches beat real-time fetches.
@Component
@RequiredArgsConstructor
@Slf4j
public class CompanyCrawlCacheAdapter implements EnrichmentProviderAdapter {

    private static final String KEY = "COMPANY_CRAWL_CACHE";

    private final CompanyCrawlPersonRepository personRepository;

    @Override public String providerKey() { return KEY; }
    @Override public String displayName() { return "Company crawl cache"; }
    @Override public boolean requiresApiKey() { return false; }

    @Override
    public EnrichmentResponse enrich(EnrichmentRequest request, String apiKey) {
        String domain = request.companyDomain();
        if (domain == null || domain.isBlank()) return EnrichmentResponse.notFound(null);
        if (isBlank(request.firstName()) && isBlank(request.lastName())) {
            return EnrichmentResponse.notFound(null);
        }

        var workspaceId = TenantContext.getWorkspaceId();
        if (workspaceId == null) return EnrichmentResponse.notFound(null);

        List<CompanyCrawlPerson> cached = personRepository
                .findByWorkspaceIdAndDomain(workspaceId, domain.toLowerCase());
        if (cached.isEmpty()) return EnrichmentResponse.notFound(null);

        String needleFirst = safe(request.firstName());
        String needleLast = safe(request.lastName());

        CompanyCrawlPerson exact = null;
        CompanyCrawlPerson partial = null;

        for (CompanyCrawlPerson p : cached) {
            if (p.getEmail() == null) continue;
            String name = safe(p.getName());
            if (name.isEmpty()) continue;

            boolean hasFirst = !needleFirst.isEmpty() && name.contains(needleFirst);
            boolean hasLast = !needleLast.isEmpty() && name.contains(needleLast);

            if (hasFirst && hasLast) { exact = p; break; }
            if ((hasFirst || hasLast) && partial == null) partial = p;
        }

        CompanyCrawlPerson hit = exact != null ? exact : partial;
        if (hit == null) return EnrichmentResponse.notFound("no name match in crawl cache");

        log.debug("Crawl cache hit: {} at {}", hit.getName(), hit.getDomain());
        // Exact-name matches get high confidence; partial matches are weaker.
        int confidence = exact != null ? 85 : 60;
        return EnrichmentResponse.success(
                hit.getEmail(), null, hit.getJobTitle(), null, confidence,
                "{\"sourceUrl\":\"" + hit.getSourceUrl() + "\"}");
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
}
