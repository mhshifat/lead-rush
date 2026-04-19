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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestrates the enrichment WATERFALL.
 *
 * HOW A WATERFALL WORKS:
 *   1. For each ENABLED provider (sorted by priority, lowest first):
 *        a. If cache has a fresh SUCCESS result (< 90 days) → use it, stop
 *        b. Else call the provider's enrich()
 *        c. If SUCCESS → persist result, stop (we found what we need)
 *        d. If NOT_FOUND/ERROR → persist result, continue to next provider
 *   2. If no provider returns SUCCESS, the "best" result we can show the user
 *      is the most recent NOT_FOUND/ERROR log line.
 *
 * The waterfall EXITS EARLY on success — we don't waste API calls once we have data.
 * This is the whole point of the pattern: pay for the cheapest/most-accurate provider first.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnrichmentWaterfallService {

    private static final int CACHE_TTL_DAYS = 90;

    /**
     * Spring auto-injects ALL beans that implement EnrichmentProviderAdapter.
     * We build a map keyed by providerKey() for O(1) lookup.
     */
    private final List<EnrichmentProviderAdapter> allAdapters;

    private final EnrichmentProviderRepository providerRepository;
    private final EnrichmentResultRepository resultRepository;
    private final EncryptionService encryptionService;
    private final DomainPatternCacheService patternCacheService;

    /**
     * Attempt to enrich a contact. Returns the FIRST successful result,
     * or the last NOT_FOUND/ERROR if no provider succeeded.
     */
    @Transactional
    public EnrichmentResult enrichContact(Contact contact) {
        UUID workspaceId = TenantContext.getWorkspaceId();

        List<EnrichmentProvider> configs = providerRepository
                .findByWorkspaceIdAndEnabledTrueOrderByPriorityAsc(workspaceId);

        if (configs.isEmpty()) {
            log.info("No enabled enrichment providers for workspace {}", workspaceId);
            return null;
        }

        // Build lookup map from key → adapter impl
        Map<String, EnrichmentProviderAdapter> adapterMap = allAdapters.stream()
                .collect(Collectors.toMap(EnrichmentProviderAdapter::providerKey, a -> a));

        // Build the request payload once — all providers get the same input
        EnrichmentProviderAdapter.EnrichmentRequest request = buildRequest(contact);

        EnrichmentResult lastResult = null;

        for (EnrichmentProvider config : configs) {
            EnrichmentProviderAdapter adapter = adapterMap.get(config.getProviderKey());
            if (adapter == null) {
                log.warn("No adapter found for provider key '{}' — misconfiguration", config.getProviderKey());
                continue;
            }

            // Check cache first — skip API call if we have a fresh SUCCESS
            var cached = resultRepository.findLatestForContactAndProvider(
                    contact.getId(), config.getProviderKey());
            if (cached.isPresent()
                    && cached.get().getStatus() == EnrichmentResult.ResultStatus.SUCCESS
                    && cached.get().getEnrichedAt().isAfter(LocalDateTime.now().minusDays(CACHE_TTL_DAYS))) {
                log.debug("Using cached {} result for contact {}", config.getProviderKey(), contact.getId());
                return cached.get();     // short-circuit — cache hit
            }

            // Call the adapter
            String decryptedKey = config.getApiKeyEncrypted() != null
                    ? encryptionService.decrypt(config.getApiKeyEncrypted())
                    : null;

            EnrichmentProviderAdapter.EnrichmentResponse response = adapter.enrich(request, decryptedKey);

            // Record usage + any error
            config.recordUse();
            if (response.status() == EnrichmentProviderAdapter.EnrichmentResponse.Status.ERROR) {
                config.setLastError(response.errorMessage());
            } else {
                config.setLastError(null);
            }
            providerRepository.save(config);

            // Save the result to cache
            EnrichmentResult result = toEntity(workspaceId, contact.getId(), config.getProviderKey(), response);
            result = resultRepository.save(result);
            lastResult = result;

            // SUCCESS → learn the pattern for this domain, then stop the waterfall
            if (result.getStatus() == EnrichmentResult.ResultStatus.SUCCESS) {
                if (result.getFoundEmail() != null && request.companyDomain() != null) {
                    patternCacheService.recordDiscoveredEmail(
                            workspaceId,
                            request.firstName(),
                            request.lastName(),
                            request.companyDomain(),
                            result.getFoundEmail(),
                            config.getProviderKey());
                }
                log.info("Enrichment SUCCESS via {} for contact {}",
                        config.getProviderKey(), contact.getId());
                return result;
            }
        }

        log.info("Enrichment waterfall exhausted for contact {} — last status: {}",
                contact.getId(), lastResult != null ? lastResult.getStatus() : "NONE");
        return lastResult;
    }

    private EnrichmentProviderAdapter.EnrichmentRequest buildRequest(Contact contact) {
        String companyName = contact.getCompany() != null ? contact.getCompany().getName() : null;
        String companyDomain = contact.getCompany() != null ? contact.getCompany().getDomain() : null;

        // If company has no domain but does have a website, try to extract domain from website
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
