package com.leadrush.enrichment.adapter.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadrush.enrichment.adapter.EnrichmentProviderAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

// UK Companies House public API — https://developer.company-information.service.gov.uk/
//
//   1. /search/companies?q=<name>    → match contact.companyName to a registered UK company
//   2. /company/{number}/officers    → pull directors / secretaries
//   3. Match contact firstName + lastName against officer names ("SURNAME, Firstname" format)
//   4. Return the officer_role as enrichment title.
//
// Companies House does NOT publish personal emails, so this adapter never returns
// foundEmail. It enriches TITLE / seniority only — useful for lead scoring and for
// confirming a UK contact is a real registered director.
//
// Auth: HTTP Basic, API key as username, password blank.
// Rate limit: 600 requests per 5 minutes per key (very generous for our volume).
// API key: register free at https://developer.company-information.service.gov.uk/
@Component
@Slf4j
public class CompaniesHouseAdapter implements EnrichmentProviderAdapter {

    private static final String KEY = "COMPANIES_HOUSE";
    private static final String BASE_URL = "https://api.company-information.service.gov.uk";
    private static final int MAX_COMPANIES_TO_PROBE = 3;

    private final RestClient http = RestClient.builder().baseUrl(BASE_URL).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override public String providerKey() { return KEY; }
    @Override public String displayName() { return "Companies House (UK)"; }
    @Override public boolean requiresApiKey() { return true; }

    @Override
    public EnrichmentResponse enrich(EnrichmentRequest request, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) return EnrichmentResponse.error("Companies House API key not configured");
        if (isBlank(request.companyName())) return EnrichmentResponse.notFound("need a company name to search CH");
        if (isBlank(request.firstName()) && isBlank(request.lastName())) {
            return EnrichmentResponse.notFound("need a contact name to match officers");
        }

        String auth = "Basic " + Base64.getEncoder()
                .encodeToString((apiKey + ":").getBytes(StandardCharsets.UTF_8));

        try {
            // Step 1: find matching companies
            JsonNode search = get("/search/companies?q=" + enc(request.companyName()) + "&items_per_page=5", auth);
            JsonNode items = search == null ? null : search.get("items");
            if (items == null || !items.isArray() || items.isEmpty()) {
                return EnrichmentResponse.notFound("no UK companies match that name");
            }

            // Step 2: for each top match, fetch officers and look for our contact by name
            int probed = 0;
            for (JsonNode company : items) {
                if (probed++ >= MAX_COMPANIES_TO_PROBE) break;
                String number = text(company, "company_number");
                if (number == null) continue;

                JsonNode officersRoot = get("/company/" + number + "/officers", auth);
                JsonNode officers = officersRoot == null ? null : officersRoot.get("items");
                if (officers == null || !officers.isArray()) continue;

                for (JsonNode officer : officers) {
                    String rawName = text(officer, "name");
                    String role = text(officer, "officer_role");
                    if (rawName == null) continue;

                    if (nameMatches(rawName, request.firstName(), request.lastName())) {
                        String title = role != null ? prettifyRole(role) : "Director";
                        log.debug("Companies House match: {} at {} ({})", rawName, text(company, "title"), role);
                        // No email (CH doesn't publish any) — we're returning title-only enrichment.
                        return EnrichmentResponse.success(
                                null, null, title, null, 90,
                                "{\"companyNumber\":\"" + number + "\",\"officerName\":\"" + rawName + "\"}");
                    }
                }
            }

            return EnrichmentResponse.notFound("contact not listed as an officer in top " + probed + " matched companies");
        } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
            return new EnrichmentResponse(
                    EnrichmentResponse.Status.RATE_LIMITED, null, null, null, null,
                    null, EnrichmentResponse.Confidence.UNKNOWN, null,
                    "Companies House rate limit exceeded");
        } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
            return EnrichmentResponse.error("Companies House API key rejected");
        } catch (Exception e) {
            log.warn("Companies House call failed: {}", e.getMessage());
            return EnrichmentResponse.error(e.getMessage());
        }
    }

    private JsonNode get(String path, String basicAuth) throws Exception {
        String body = http.get()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, basicAuth)
                .header(HttpHeaders.ACCEPT, "application/json")
                .retrieve()
                .body(String.class);
        return body == null ? null : mapper.readTree(body);
    }

    // Companies House returns names as "SURNAME, Firstname Middle" (director case) OR
    // "Mr John SMITH" (corporate officers sometimes). We match on case-insensitive substring
    // of both first and last — permissive but prevents "JONES" matching "Jones" alone.
    private boolean nameMatches(String rawName, String first, String last) {
        String n = rawName.toLowerCase(Locale.ROOT);
        boolean firstOk = isBlank(first) || n.contains(first.toLowerCase(Locale.ROOT));
        boolean lastOk = isBlank(last) || n.contains(last.toLowerCase(Locale.ROOT));
        // Require at least one exact part + the other present or explicitly absent
        if (isBlank(first)) return lastOk;
        if (isBlank(last)) return firstOk;
        return firstOk && lastOk;
    }

    // "nominee-director" → "Nominee Director"
    private String prettifyRole(String role) {
        List<String> parts = List.of(role.split("-"));
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) sb.append(' ');
            if (!p.isEmpty()) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field)) return null;
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
}
