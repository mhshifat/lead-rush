package com.leadrush.enrichment.adapter.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadrush.enrichment.adapter.EnrichmentProviderAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// People Data Labs Person Enrichment API — https://docs.peopledatalabs.com/docs/person-enrichment-api
//
// Uses GET /v5/person/enrich with query params. Good hit rate on US/EU professional
// profiles but is the most expensive tier. Intended as the LAST step of the waterfall,
// only reached when every free tier has already failed.
//
// Requires PDL_API_KEY set on the enrichment_providers row for this workspace.
@Component
@Slf4j
public class PeopleDataLabsAdapter implements EnrichmentProviderAdapter {

    private static final String KEY = "PDL";
    private static final String BASE_URL = "https://api.peopledatalabs.com/v5";

    private final RestClient restClient = RestClient.builder().baseUrl(BASE_URL).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override public String providerKey() { return KEY; }
    @Override public String displayName() { return "People Data Labs"; }
    @Override public boolean requiresApiKey() { return true; }

    @Override
    public EnrichmentResponse enrich(EnrichmentRequest request, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) return EnrichmentResponse.error("PDL API key not configured");

        // PDL needs enough signal to locate a person: email OR (name AND company/domain).
        boolean haveEmail = request.existingEmail() != null && !request.existingEmail().isBlank();
        boolean haveName = !(isBlank(request.firstName()) && isBlank(request.lastName()));
        boolean haveCompany = !isBlank(request.companyName()) || !isBlank(request.companyDomain());
        if (!haveEmail && !(haveName && haveCompany)) return EnrichmentResponse.notFound(null);

        try {
            String body = restClient.get()
                    .uri(uriBuilder -> {
                        var b = uriBuilder.path("/person/enrich");
                        if (haveEmail) b.queryParam("email", request.existingEmail());
                        if (!isBlank(request.firstName())) b.queryParam("first_name", request.firstName());
                        if (!isBlank(request.lastName())) b.queryParam("last_name", request.lastName());
                        if (!isBlank(request.companyName())) b.queryParam("company", request.companyName());
                        if (!isBlank(request.companyDomain())) b.queryParam("company", request.companyDomain());
                        // Minimum likelihood 2 — rejects matches PDL itself considers low-confidence.
                        b.queryParam("min_likelihood", 2);
                        return b.build();
                    })
                    .header("X-Api-Key", apiKey)
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(String.class);

            return parse(body);
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            // PDL returns 404 when no match is found — that's expected, not an error.
            return EnrichmentResponse.notFound(e.getResponseBodyAsString());
        } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
            return new EnrichmentResponse(
                    EnrichmentResponse.Status.RATE_LIMITED, null, null, null, null, null, null,
                    "PDL rate limit exceeded");
        } catch (Exception e) {
            log.warn("PDL call failed: {}", e.getMessage());
            return EnrichmentResponse.error(e.getMessage());
        }
    }

    // PDL response shape:
    //   { "status": 200, "likelihood": 10, "data": { "work_email": "...", "job_title": "...",
    //     "linkedin_url": "...", "mobile_phone": "...", ... } }
    private EnrichmentResponse parse(String json) throws Exception {
        JsonNode root = mapper.readTree(json);
        JsonNode data = root.get("data");
        if (data == null || data.isNull()) return EnrichmentResponse.notFound(json);

        String email = firstNonNull(
                text(data, "work_email"),
                text(data, "recommended_personal_email"),
                firstEmailInArray(data.get("emails")));
        String phone = text(data, "mobile_phone");
        String title = text(data, "job_title");
        String linkedinUrl = text(data, "linkedin_url");
        Integer likelihood = root.has("likelihood") && !root.get("likelihood").isNull()
                ? root.get("likelihood").asInt() : null;

        if (email == null && phone == null && title == null && linkedinUrl == null) {
            return EnrichmentResponse.notFound(json);
        }
        // Map PDL 0-10 "likelihood" onto the shared 0-100 confidence scale.
        Integer confidence = likelihood != null ? likelihood * 10 : null;
        return EnrichmentResponse.success(email, phone, title, linkedinUrl, confidence, json);
    }

    private static String firstEmailInArray(JsonNode emails) {
        if (emails == null || !emails.isArray() || emails.isEmpty()) return null;
        JsonNode first = emails.get(0);
        return first == null ? null : text(first, "address");
    }

    private static String firstNonNull(String... vals) {
        for (String v : vals) if (v != null && !v.isBlank()) return v;
        return null;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field)) return null;
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
}
