package com.leadrush.enrichment.adapter;

/**
 * Adapter for third-party enrichment providers (Hunter, Clearbit, Dropcontact, etc.).
 *
 * Each provider implementation:
 *   - Declares its unique key (providerKey)
 *   - Declares a human-readable display name
 *   - Declares whether it needs an API key
 *   - Implements enrich() to call the provider's API
 *
 * The EnrichmentWaterfallService tries implementations in priority order
 * (from EnrichmentProvider config) until a field is resolved.
 */
public interface EnrichmentProviderAdapter {

    /** Machine-readable provider key — matches enrichment_providers.provider_key */
    String providerKey();

    /** Human-readable name for the UI (e.g., "Hunter.io") */
    String displayName();

    /** Does this provider need an API key configured? */
    boolean requiresApiKey();

    /**
     * Attempt to enrich a contact.
     *
     * @param request The enrichment input (name, company, existing email hint, etc.)
     * @param apiKey  Decrypted API key (null if requiresApiKey() == false)
     * @return        Enrichment result (never null — always returns status + possibly empty fields)
     */
    EnrichmentResponse enrich(EnrichmentRequest request, String apiKey);

    // ── Shared DTOs (adapter-specific, not provider-specific) ──

    record EnrichmentRequest(
        String firstName,
        String lastName,
        String companyName,
        String companyDomain,
        String existingEmail       // optional — some providers find details by email
    ) {}

    record EnrichmentResponse(
        Status status,
        String foundEmail,
        String foundPhone,
        String foundTitle,
        String foundLinkedinUrl,
        Integer confidenceScore,
        String rawResponseJson,
        String errorMessage
    ) {
        public enum Status {
            SUCCESS, NOT_FOUND, ERROR, RATE_LIMITED
        }

        public static EnrichmentResponse success(
                String email, String phone, String title, String linkedinUrl,
                Integer confidence, String rawJson) {
            return new EnrichmentResponse(
                    Status.SUCCESS, email, phone, title, linkedinUrl,
                    confidence, rawJson, null);
        }

        public static EnrichmentResponse notFound(String rawJson) {
            return new EnrichmentResponse(
                    Status.NOT_FOUND, null, null, null, null, null, rawJson, null);
        }

        public static EnrichmentResponse error(String errorMessage) {
            return new EnrichmentResponse(
                    Status.ERROR, null, null, null, null, null, null, errorMessage);
        }
    }
}
