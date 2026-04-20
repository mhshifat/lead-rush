package com.leadrush.enrichment.service;

import com.leadrush.common.exception.ResourceNotFoundException;
import com.leadrush.contact.entity.Contact;
import com.leadrush.contact.entity.ContactEmail;
import com.leadrush.contact.entity.ContactSource;
import com.leadrush.contact.repository.ContactRepository;
import com.leadrush.enrichment.adapter.EnrichmentProviderAdapter;
import com.leadrush.enrichment.dto.*;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Top-level enrichment API — provider config management + enrich actions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnrichmentService {

    private final EnrichmentProviderRepository providerRepository;
    private final EnrichmentResultRepository resultRepository;
    private final EnrichmentWaterfallService waterfallService;
    private final ContactRepository contactRepository;
    private final EncryptionService encryptionService;
    private final List<EnrichmentProviderAdapter> allAdapters;

    // ── Provider config management ──

    /**
     * List all provider configs for this workspace, auto-creating entries for any
     * adapter that doesn't have a config yet (so the UI shows every available provider).
     */
    @Transactional
    public List<EnrichmentProviderResponse> listProviders() {
        UUID workspaceId = TenantContext.getWorkspaceId();
        List<EnrichmentProvider> configs = providerRepository.findByWorkspaceIdOrderByPriorityAsc(workspaceId);

        // Ensure every known adapter has a config row (disabled by default).
        // Priority follows the waterfall design: free/instant tiers first, paid last.
        Map<String, EnrichmentProvider> byKey = configs.stream()
                .collect(Collectors.toMap(EnrichmentProvider::getProviderKey, c -> c));

        for (EnrichmentProviderAdapter adapter : allAdapters) {
            if (!byKey.containsKey(adapter.providerKey())) {
                EnrichmentProvider fresh = EnrichmentProvider.builder()
                        .providerKey(adapter.providerKey())
                        .enabled(false)
                        .priority(defaultPriority(adapter.providerKey()))
                        .build();
                fresh.setWorkspaceId(workspaceId);
                fresh = providerRepository.save(fresh);
                byKey.put(adapter.providerKey(), fresh);
            }
        }

        return byKey.values().stream()
                .sorted((a, b) -> Integer.compare(a.getPriority(), b.getPriority()))
                .map(this::toProviderResponse)
                .toList();
    }

    /**
     * Update provider config — enable/disable, priority, API key.
     * API key is only updated if the caller provides a non-null value.
     */
    @Transactional
    public EnrichmentProviderResponse updateProvider(EnrichmentProviderRequest request) {
        UUID workspaceId = TenantContext.getWorkspaceId();

        // Make sure this provider key is actually known
        EnrichmentProviderAdapter adapter = findAdapter(request.getProviderKey());
        if (adapter == null) {
            throw new ResourceNotFoundException("EnrichmentProvider", request.getProviderKey());
        }

        EnrichmentProvider config = providerRepository
                .findByWorkspaceIdAndProviderKey(workspaceId, request.getProviderKey())
                .orElseGet(() -> {
                    EnrichmentProvider fresh = EnrichmentProvider.builder()
                            .providerKey(request.getProviderKey())
                            .build();
                    fresh.setWorkspaceId(workspaceId);
                    return fresh;
                });

        if (request.getEnabled() != null) config.setEnabled(request.getEnabled());
        if (request.getPriority() != null) config.setPriority(request.getPriority());
        if (request.getApiKey() != null) {
            if (request.getApiKey().isBlank()) {
                config.setApiKeyEncrypted(null);    // clear the key
            } else {
                config.setApiKeyEncrypted(encryptionService.encrypt(request.getApiKey()));
            }
        }

        config = providerRepository.save(config);
        return toProviderResponse(config);
    }

    // ── Enrichment actions ──

    /**
     * Enrich a single contact. Runs the waterfall and — on success — updates the contact's
     * fields with newly found data (only fills in missing fields, never overwrites).
     */
    @Transactional
    public EnrichmentResultResponse enrichContact(UUID contactId) {
        UUID workspaceId = TenantContext.getWorkspaceId();

        Contact contact = contactRepository.findByIdAndWorkspaceId(contactId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Contact", contactId));

        var outcome = waterfallService.enrichContactFully(contact);
        EnrichmentResult result = outcome.best();
        if (result == null && outcome.allEmails().isEmpty()) {
            return null;
        }

        // On SUCCESS: apply the enriched fields to the contact (only fill missing fields)
        if (result != null && result.getStatus() == EnrichmentResult.ResultStatus.SUCCESS) {
            applyEnrichmentToContact(contact, result);
        }
        // Always persist every distinct email the waterfall found, each tagged
        // with its source adapter + qualitative confidence.
        applyDiscoveredEmails(contact, outcome.allEmails());
        contactRepository.save(contact);

        return result != null ? toResultResponse(result) : null;
    }

    /**
     * Bulk enrichment — enrich multiple contacts. Returns a summary count.
     * In production, this would be a Spring Batch job for large lists.
     */
    @Transactional
    public BulkEnrichmentResult enrichBulk(List<UUID> contactIds) {
        int succeeded = 0, notFound = 0, errored = 0;

        for (UUID id : contactIds) {
            try {
                EnrichmentResultResponse res = enrichContact(id);
                if (res == null) {
                    errored++;
                } else {
                    switch (res.getStatus()) {
                        case "SUCCESS" -> succeeded++;
                        case "NOT_FOUND" -> notFound++;
                        default -> errored++;
                    }
                }
            } catch (Exception e) {
                log.warn("Bulk enrichment failed for contact {}: {}", id, e.getMessage());
                errored++;
            }
        }

        return new BulkEnrichmentResult(contactIds.size(), succeeded, notFound, errored);
    }

    public record BulkEnrichmentResult(int total, int succeeded, int notFound, int errored) {}

    @Transactional(readOnly = true)
    public List<EnrichmentResultResponse> listResultsForContact(UUID contactId) {
        return resultRepository.findByContactIdOrderByEnrichedAtDesc(contactId).stream()
                .map(this::toResultResponse)
                .toList();
    }

    // ── Internal helpers ──

    /**
     * Merge enrichment data into contact — only fills empty fields, never overwrites.
     * NOTE: email handling is split out into {@link #applyDiscoveredEmails} so that
     * every discovered email across the waterfall (not just the best one) is stored.
     */
    private void applyEnrichmentToContact(Contact contact, EnrichmentResult result) {
        if (result.getFoundTitle() != null && (contact.getTitle() == null || contact.getTitle().isBlank())) {
            contact.setTitle(result.getFoundTitle());
        }
        if (result.getFoundLinkedinUrl() != null
                && (contact.getLinkedinUrl() == null || contact.getLinkedinUrl().isBlank())) {
            contact.setLinkedinUrl(result.getFoundLinkedinUrl());
        }

        // Tag source as enriched if it wasn't set
        if (contact.getSource() == null) {
            contact.setSource(ContactSource.ENRICHMENT);
        }
    }

    /**
     * Persists every distinct email the waterfall discovered. Each row:
     *   - has its own {@code verificationStatus} derived from the adapter's confidence
     *   - is tagged with {@code source = providerKey} for "Hunter — verified" UI
     *   - is deduped by lowercase email so re-runs don't add duplicates
     *
     * The first row added (or the first VERIFIED if there is one) becomes primary
     * when the contact has no primary email yet.
     */
    private void applyDiscoveredEmails(
            Contact contact,
            List<EnrichmentWaterfallService.DiscoveredEmail> discovered
    ) {
        if (discovered == null || discovered.isEmpty()) return;

        // Existing addresses (case-insensitive) we already have on this contact.
        // Re-running enrichment shouldn't produce duplicate rows.
        var existing = contact.getEmails().stream()
                .map(e -> e.getEmail().toLowerCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());

        // Sort so VERIFIED lands first, then LIKELY → UNKNOWN → GUESSED.
        // Whichever lands first becomes the primary if the contact has none yet.
        var sorted = discovered.stream()
                .sorted(java.util.Comparator.comparingInt(
                        (EnrichmentWaterfallService.DiscoveredEmail d) -> -rank(d.confidence())))
                .toList();

        boolean contactHadPrimary = contact.getPrimaryEmail() != null;
        boolean primaryAssigned = contactHadPrimary;

        for (var d : sorted) {
            if (d.email() == null || d.email().isBlank()) continue;
            if (existing.contains(d.email().toLowerCase(java.util.Locale.ROOT))) continue;

            ContactEmail row = ContactEmail.builder()
                    .email(d.email())
                    .emailType(ContactEmail.EmailType.WORK)
                    .primary(!primaryAssigned)   // first unseen + caller has no primary → become primary
                    .verificationStatus(mapConfidence(d.confidence()))
                    .source(d.providerKey())
                    .build();
            row.setWorkspaceId(contact.getWorkspaceId());
            contact.addEmail(row);
            existing.add(d.email().toLowerCase(java.util.Locale.ROOT));
            if (!primaryAssigned) primaryAssigned = true;
        }
    }

    /** Qualitative Confidence → persisted VerificationStatus. */
    private static ContactEmail.VerificationStatus mapConfidence(
            EnrichmentProviderAdapter.EnrichmentResponse.Confidence c
    ) {
        if (c == null) return ContactEmail.VerificationStatus.UNKNOWN;
        return switch (c) {
            case VERIFIED -> ContactEmail.VerificationStatus.VERIFIED;
            case LIKELY   -> ContactEmail.VerificationStatus.LIKELY;
            case UNKNOWN  -> ContactEmail.VerificationStatus.UNKNOWN;
            case GUESSED  -> ContactEmail.VerificationStatus.GUESSED;
        };
    }

    private static int rank(EnrichmentProviderAdapter.EnrichmentResponse.Confidence c) {
        return switch (c) {
            case VERIFIED -> 4;
            case LIKELY   -> 3;
            case UNKNOWN  -> 2;
            case GUESSED  -> 1;
        };
    }

    private EnrichmentProviderAdapter findAdapter(String key) {
        return allAdapters.stream()
                .filter(a -> a.providerKey().equals(key))
                .findFirst()
                .orElse(null);
    }

    // Waterfall order: free tiers first (cache, scrape, github), then the paid APIs.
    // Companies House runs last as a title-only enrichment.
    // Lower number = tried first.
    private static int defaultPriority(String providerKey) {
        return switch (providerKey) {
            case "PATTERN_CACHE"        -> 10;
            case "COMPANY_CRAWL_CACHE"  -> 15;
            case "GITHUB"               -> 20;
            case "SITEMAP_CRAWLER"      -> 25;
            case "WEBSITE_SCRAPER"      -> 30;
            case "MOCK"                 -> 50;
            case "HUNTER"               -> 60;
            case "PDL"                  -> 70;
            case "COMPANIES_HOUSE"      -> 80;
            default                     -> 100;
        };
    }

    private EnrichmentProviderResponse toProviderResponse(EnrichmentProvider config) {
        EnrichmentProviderAdapter adapter = findAdapter(config.getProviderKey());
        return EnrichmentProviderResponse.builder()
                .id(config.getId())
                .providerKey(config.getProviderKey())
                .displayName(adapter != null ? adapter.displayName() : config.getProviderKey())
                .enabled(config.isEnabled())
                .priority(config.getPriority())
                .hasApiKey(config.getApiKeyEncrypted() != null)
                .requiresApiKey(adapter != null && adapter.requiresApiKey())
                .callsThisMonth(config.getCallsThisMonth())
                .lastUsedAt(config.getLastUsedAt())
                .lastError(config.getLastError())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }

    private EnrichmentResultResponse toResultResponse(EnrichmentResult result) {
        return EnrichmentResultResponse.builder()
                .id(result.getId())
                .contactId(result.getContactId())
                .providerKey(result.getProviderKey())
                .status(result.getStatus().name())
                .foundEmail(result.getFoundEmail())
                .foundPhone(result.getFoundPhone())
                .foundTitle(result.getFoundTitle())
                .foundLinkedinUrl(result.getFoundLinkedinUrl())
                .confidenceScore(result.getConfidenceScore())
                .errorMessage(result.getErrorMessage())
                .enrichedAt(result.getEnrichedAt())
                .build();
    }
}
