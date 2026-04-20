package com.leadrush.enrichment.service;

import com.leadrush.contact.entity.Contact;
import com.leadrush.enrichment.adapter.EnrichmentProviderAdapter;
import com.leadrush.enrichment.entity.EnrichmentProvider;
import com.leadrush.enrichment.entity.EnrichmentResult;
import com.leadrush.enrichment.repository.EnrichmentProviderRepository;
import com.leadrush.enrichment.repository.EnrichmentResultRepository;
import com.leadrush.security.EncryptionService;
import com.leadrush.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestrates the enrichment WATERFALL.
 *
 * HOW THE WATERFALL WORKS:
 *   1. For each ENABLED provider (sorted by priority, lowest first):
 *        a. If cache has a fresh SUCCESS result (< 90 days) → reuse its data, skip the API call
 *        b. Else call the provider's enrich()
 *        c. Save the result row (SUCCESS/NOT_FOUND/ERROR) for audit + cache
 *        d. If SUCCESS with an email, add it to the aggregated email list
 *   2. Two stopping rules:
 *        - Hit a VERIFIED email → stop (save paid API calls, the best answer won)
 *        - Hit a LIKELY email from a FREE tier → continue only if a later adapter
 *          might have VERIFIED (i.e. Hunter/PDL still unused)
 *   3. Return the "best" single result for legacy UI plus the full email list.
 *
 * Unlike the old "first hit wins" behaviour, we now surface every distinct
 * email we find so users can pick between "Hunter — verified" and "Pattern
 * guess — unverified" on the contact page.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnrichmentWaterfallService {

    private static final int CACHE_TTL_DAYS = 90;
    /** Adapter priority threshold — anything <= is considered "free tier". */
    private static final int FREE_TIER_MAX_PRIORITY = 50;

    private final List<EnrichmentProviderAdapter> allAdapters;
    private final EnrichmentProviderRepository providerRepository;
    private final EnrichmentResultRepository resultRepository;
    private final EncryptionService encryptionService;
    private final DomainPatternCacheService patternCacheService;

    /** An email discovered by the waterfall, tagged with its source adapter. */
    public record DiscoveredEmail(
            String email,
            String providerKey,
            EnrichmentProviderAdapter.EnrichmentResponse.Confidence confidence,
            Integer confidenceScore,
            String foundTitle,
            String foundLinkedinUrl,
            String foundPhone
    ) {}

    /** Full outcome of a waterfall run — best single result + every email found. */
    public record WaterfallOutcome(
            EnrichmentResult best,
            List<DiscoveredEmail> allEmails
    ) {
        public static WaterfallOutcome empty() {
            return new WaterfallOutcome(null, List.of());
        }
    }

    /**
     * Legacy signature — returns only the "best" single EnrichmentResult for
     * callers that haven't migrated to the collect-all response yet.
     */
    public EnrichmentResult enrichContact(Contact contact) {
        return enrichContactFully(contact).best();
    }

    /**
     * Runs the waterfall and returns both the best single result AND every
     * email any adapter produced. Duplicate emails across adapters are deduped
     * by lowercase address, keeping the highest-confidence source.
     */
    @Transactional
    public WaterfallOutcome enrichContactFully(Contact contact) {
        UUID workspaceId = TenantContext.getWorkspaceId();

        List<EnrichmentProvider> configs = providerRepository
                .findByWorkspaceIdAndEnabledTrueOrderByPriorityAsc(workspaceId);

        if (configs.isEmpty()) {
            log.info("No enabled enrichment providers for workspace {}", workspaceId);
            return WaterfallOutcome.empty();
        }

        Map<String, EnrichmentProviderAdapter> adapterMap = allAdapters.stream()
                .collect(Collectors.toMap(EnrichmentProviderAdapter::providerKey, a -> a));

        EnrichmentProviderAdapter.EnrichmentRequest request = buildRequest(contact);

        // LinkedHashMap keyed by lowercased email preserves insertion order (priority order).
        // On duplicate emails, we KEEP the higher-confidence version — that's why we
        // re-check on every insert rather than blindly putIfAbsent.
        Map<String, DiscoveredEmail> emailByAddress = new LinkedHashMap<>();
        EnrichmentResult best = null;
        boolean hasVerified = false;

        for (EnrichmentProvider config : configs) {
            EnrichmentProviderAdapter adapter = adapterMap.get(config.getProviderKey());
            if (adapter == null) {
                log.warn("No adapter for '{}' — misconfig", config.getProviderKey());
                continue;
            }

            // Cost-saving stopping rule: if a free-tier run already produced a
            // VERIFIED email, don't call paid APIs (Hunter/PDL are typically priority >= 50).
            if (hasVerified && config.getPriority() >= FREE_TIER_MAX_PRIORITY) {
                log.debug("Skipping paid adapter {} — already have a VERIFIED result", config.getProviderKey());
                continue;
            }

            EnrichmentResult result = runOneAdapter(contact, workspaceId, config, adapter, request);
            if (result == null) continue;

            // Track the "best" result for the single-result UI. Priority-order
            // means the first SUCCESS wins — same behaviour as the old waterfall.
            if (best == null && result.getStatus() == EnrichmentResult.ResultStatus.SUCCESS) {
                best = result;
            }

            if (result.getStatus() == EnrichmentResult.ResultStatus.SUCCESS
                    && result.getFoundEmail() != null) {
                var discovered = toDiscoveredEmail(result);
                String key = discovered.email().toLowerCase(Locale.ROOT);
                var existing = emailByAddress.get(key);
                // Keep whichever has higher confidence; insertion order preserved otherwise.
                if (existing == null || rank(discovered.confidence()) > rank(existing.confidence())) {
                    emailByAddress.put(key, discovered);
                }
                if (discovered.confidence()
                        == EnrichmentProviderAdapter.EnrichmentResponse.Confidence.VERIFIED) {
                    hasVerified = true;
                }

                // Learn the pattern for this domain — helps future imports at same company.
                if (request.companyDomain() != null) {
                    patternCacheService.recordDiscoveredEmail(
                            workspaceId,
                            request.firstName(),
                            request.lastName(),
                            request.companyDomain(),
                            result.getFoundEmail(),
                            config.getProviderKey());
                }
            }
        }

        log.info("Waterfall for contact {}: {} distinct email(s), best via {}",
                contact.getId(),
                emailByAddress.size(),
                best != null ? best.getProviderKey() : "none");

        return new WaterfallOutcome(best, new ArrayList<>(emailByAddress.values()));
    }

    /**
     * Runs a single adapter, persists its EnrichmentResult, and returns the row.
     * Returns null on misconfig/skip. Uses the cache if a recent SUCCESS exists.
     */
    private EnrichmentResult runOneAdapter(
            Contact contact,
            UUID workspaceId,
            EnrichmentProvider config,
            EnrichmentProviderAdapter adapter,
            EnrichmentProviderAdapter.EnrichmentRequest request
    ) {
        var cached = resultRepository.findLatestForContactAndProvider(
                contact.getId(), config.getProviderKey());
        if (cached.isPresent()
                && cached.get().getStatus() == EnrichmentResult.ResultStatus.SUCCESS
                && cached.get().getEnrichedAt().isAfter(LocalDateTime.now().minusDays(CACHE_TTL_DAYS))) {
            log.debug("Cache hit: {} for contact {}", config.getProviderKey(), contact.getId());
            return cached.get();
        }

        String decryptedKey = config.getApiKeyEncrypted() != null
                ? encryptionService.decrypt(config.getApiKeyEncrypted())
                : null;

        EnrichmentProviderAdapter.EnrichmentResponse response;
        try {
            response = adapter.enrich(request, decryptedKey);
        } catch (Exception e) {
            log.warn("Adapter {} threw: {}", config.getProviderKey(), e.getMessage());
            response = EnrichmentProviderAdapter.EnrichmentResponse.error(e.getMessage());
        }

        config.recordUse();
        config.setLastError(
                response.status() == EnrichmentProviderAdapter.EnrichmentResponse.Status.ERROR
                        ? response.errorMessage()
                        : null);
        providerRepository.save(config);

        EnrichmentResult row = toEntity(workspaceId, contact.getId(), config.getProviderKey(), response);
        return resultRepository.save(row);
    }

    private DiscoveredEmail toDiscoveredEmail(EnrichmentResult r) {
        // EnrichmentResult doesn't persist the qualitative Confidence level yet —
        // we infer it from the adapter key + numeric score as a reasonable default.
        var level = inferConfidence(r.getProviderKey(), r.getConfidenceScore());
        return new DiscoveredEmail(
                r.getFoundEmail(),
                r.getProviderKey(),
                level,
                r.getConfidenceScore(),
                r.getFoundTitle(),
                r.getFoundLinkedinUrl(),
                r.getFoundPhone()
        );
    }

    /**
     * Default confidence-mapping per adapter — used when we read from the cached
     * EnrichmentResult row (which doesn't store the qualitative level yet).
     */
    private static EnrichmentProviderAdapter.EnrichmentResponse.Confidence inferConfidence(
            String providerKey, Integer score
    ) {
        return switch (providerKey) {
            case "HUNTER" -> score != null && score >= 90
                    ? EnrichmentProviderAdapter.EnrichmentResponse.Confidence.VERIFIED
                    : score != null && score >= 60
                        ? EnrichmentProviderAdapter.EnrichmentResponse.Confidence.LIKELY
                        : EnrichmentProviderAdapter.EnrichmentResponse.Confidence.UNKNOWN;
            case "PDL" -> score != null && score >= 90
                    ? EnrichmentProviderAdapter.EnrichmentResponse.Confidence.VERIFIED
                    : EnrichmentProviderAdapter.EnrichmentResponse.Confidence.LIKELY;
            case "GITHUB" -> EnrichmentProviderAdapter.EnrichmentResponse.Confidence.LIKELY;
            case "COMPANY_CRAWL_CACHE", "SITEMAP_CRAWLER", "WEBSITE_SCRAPER"
                    -> EnrichmentProviderAdapter.EnrichmentResponse.Confidence.UNKNOWN;
            case "PATTERN_CACHE" -> EnrichmentProviderAdapter.EnrichmentResponse.Confidence.GUESSED;
            default -> EnrichmentProviderAdapter.EnrichmentResponse.Confidence.UNKNOWN;
        };
    }

    /** Rank used to pick the "best" email when the same address comes from multiple adapters. */
    private static int rank(EnrichmentProviderAdapter.EnrichmentResponse.Confidence c) {
        return switch (c) {
            case VERIFIED -> 4;
            case LIKELY -> 3;
            case UNKNOWN -> 2;
            case GUESSED -> 1;
        };
    }

    private EnrichmentProviderAdapter.EnrichmentRequest buildRequest(Contact contact) {
        String companyName = contact.getCompany() != null ? contact.getCompany().getName() : null;
        String companyDomain = contact.getCompany() != null ? contact.getCompany().getDomain() : null;

        if ((companyDomain == null || companyDomain.isBlank())
                && contact.getCompany() != null
                && contact.getCompany().getWebsite() != null) {
            companyDomain = extractDomain(contact.getCompany().getWebsite());
        }

        return new EnrichmentProviderAdapter.EnrichmentRequest(
                contact.getFirstName(),
                contact.getLastName(),
                companyName,
                companyDomain,
                contact.getPrimaryEmail()
        );
    }

    private String extractDomain(String website) {
        if (website == null) return null;
        String s = website.trim()
                .replaceFirst("^https?://", "")
                .replaceFirst("^www\\.", "");
        int slash = s.indexOf('/');
        return slash > 0 ? s.substring(0, slash) : s;
    }

    private EnrichmentResult toEntity(
            UUID workspaceId, UUID contactId, String providerKey,
            EnrichmentProviderAdapter.EnrichmentResponse response
    ) {
        EnrichmentResult result = EnrichmentResult.builder()
                .contactId(contactId)
                .providerKey(providerKey)
                .status(EnrichmentResult.ResultStatus.valueOf(response.status().name()))
                .rawResponse(response.rawResponseJson())
                .foundEmail(response.foundEmail())
                .foundPhone(response.foundPhone())
                .foundTitle(response.foundTitle())
                .foundLinkedinUrl(response.foundLinkedinUrl())
                .confidenceScore(response.confidenceScore())
                .errorMessage(response.errorMessage())
                .enrichedAt(LocalDateTime.now())
                .build();
        result.setWorkspaceId(workspaceId);
        return result;
    }
}
