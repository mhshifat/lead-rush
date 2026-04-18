package com.leadrush.enrichment.adapter.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadrush.enrichment.adapter.EnrichmentProviderAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Hunter.io enrichment (email-finder). Docs: https://hunter.io/api-documentation */
@Component
@Slf4j
public class HunterEnrichmentAdapter implements EnrichmentProviderAdapter {

    private static final String KEY = "HUNTER";
    private static final String BASE_URL = "https://api.hunter.io/v2/email-finder";

    private final RestClient restClient = RestClient.builder()
            .baseUrl(BASE_URL)
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override public String providerKey() { return KEY; }
    @Override public String displayName() { return "Hunter.io"; }
    @Override public boolean requiresApiKey() { return true; }

    @Override
    public EnrichmentResponse enrich(EnrichmentRequest request, String apiKey) {
        // Hunter needs at minimum: domain + (first_name + last_name) OR full_name.
        if (request.companyDomain() == null || request.companyDomain().isBlank()) {
            return EnrichmentResponse.notFound(null);
        }
        if ((request.firstName() == null || request.firstName().isBlank())
                && (request.lastName() == null || request.lastName().isBlank())) {
            return EnrichmentResponse.notFound(null);
        }
        if (apiKey == null || apiKey.isBlank()) {
            return EnrichmentResponse.error("Hunter API key not configured");
        }

        try {
            String response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("domain", request.companyDomain())
                            .queryParam("first_name", safeParam(request.firstName()))
                            .queryParam("last_name", safeParam(request.lastName()))
                            .queryParam("api_key", apiKey)
                            .build())
                    .retrieve()
                    .body(String.class);

            return parseResponse(response);
        } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
            log.warn("Hunter rate limit hit: {}", e.getMessage());
            return new EnrichmentResponse(
                    EnrichmentResponse.Status.RATE_LIMITED, null, null, null, null, null, null,
                    "Rate limit exceeded");
        } catch (Exception e) {
            log.warn("Hunter API call failed: {}", e.getMessage());
            return EnrichmentResponse.error(e.getMessage());
        }
    }

    /**
     * Parse Hunter's JSON response. Shape:
     *   {
     *     "data": {
     *       "email": "john.doe@acme.com",
     *       "score": 92,
     *       "position": "VP of Sales",
     *       ...
     *     }
     *   }
     */
    private EnrichmentResponse parseResponse(String json) throws Exception {
        JsonNode root = mapper.readTree(json);
        JsonNode data = root.get("data");

        if (data == null || data.isNull() || !data.has("email") || data.get("email").isNull()) {
            return EnrichmentResponse.notFound(json);
        }

        String email = text(data, "email");
        Integer score = data.has("score") && !data.get("score").isNull()
                ? data.get("score").asInt() : null;
        String title = text(data, "position");
        String linkedinUrl = text(data, "linkedin_url");

        return EnrichmentResponse.success(email, null, title, linkedinUrl, score, json);
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.has(field)) return null;
        JsonNode val = node.get(field);
        return val == null || val.isNull() ? null : val.asText();
    }

    private String safeParam(String s) {
        return s == null ? "" : s;
    }
}
