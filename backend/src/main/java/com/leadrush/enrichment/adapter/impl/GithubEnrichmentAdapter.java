package com.leadrush.enrichment.adapter.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadrush.enrichment.adapter.EnrichmentProviderAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// Resolves developer emails via the public GitHub API.
// 1. Search users by full name (`GET /search/users?q=...+in:name`).
// 2. For each candidate username, list their public push events (`/users/{u}/events/public`).
// 3. Pull author emails out of the commit payloads, skipping the `@users.noreply.github.com`
//    placeholder GitHub uses when the user has "Keep my email addresses private" enabled.
//
// Anonymous calls are rate-limited to 60/hour per IP. An optional token (passed as the
// enrichment_providers.api_key_encrypted value) raises the limit to 5,000/hour.
@Component
@Slf4j
public class GithubEnrichmentAdapter implements EnrichmentProviderAdapter {

    private static final String KEY = "GITHUB";
    private static final String BASE_URL = "https://api.github.com";
    private static final String NOREPLY_SUFFIX = "@users.noreply.github.com";

    private final RestClient restClient = RestClient.builder().baseUrl(BASE_URL).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override public String providerKey() { return KEY; }
    @Override public String displayName() { return "GitHub (dev emails)"; }
    @Override public boolean requiresApiKey() { return false; }   // token optional, just raises rate limit

    @Override
    public EnrichmentResponse enrich(EnrichmentRequest request, String apiKey) {
        if ((isBlank(request.firstName()) && isBlank(request.lastName()))) {
            return EnrichmentResponse.notFound(null);
        }

        String fullName = (safe(request.firstName()) + " " + safe(request.lastName())).trim();
        try {
            JsonNode search = get("/search/users?q=" + enc(fullName) + "+in:name&per_page=5", apiKey);
            JsonNode items = search == null ? null : search.get("items");
            if (items == null || !items.isArray() || items.isEmpty()) {
                return EnrichmentResponse.notFound(search == null ? null : search.toString());
            }

            for (JsonNode user : items) {
                String login = text(user, "login");
                if (login == null) continue;

                // Prefer the public profile email if the user has set one.
                JsonNode profile = get("/users/" + login, apiKey);
                String profileEmail = text(profile, "email");
                if (profileEmail != null && !profileEmail.endsWith(NOREPLY_SUFFIX)) {
                    return successFromGithub(profileEmail, login, profile.toString(), request);
                }

                // Otherwise scan recent push events for author emails.
                JsonNode events = get("/users/" + login + "/events/public", apiKey);
                if (events == null || !events.isArray()) continue;

                for (JsonNode event : events) {
                    if (!"PushEvent".equals(text(event, "type"))) continue;
                    JsonNode commits = event.at("/payload/commits");
                    if (!commits.isArray()) continue;
                    for (JsonNode commit : commits) {
                        String email = text(commit.get("author"), "email");
                        if (email != null && !email.endsWith(NOREPLY_SUFFIX)) {
                            return successFromGithub(email, login, commit.toString(), request);
                        }
                    }
                }
            }

            return EnrichmentResponse.notFound(items.toString());
        } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
            return new EnrichmentResponse(
                    EnrichmentResponse.Status.RATE_LIMITED,
                    null, null, null, null, null,
                    EnrichmentResponse.Confidence.UNKNOWN, null,
                    "GitHub rate limit exceeded");
        } catch (Exception e) {
            log.warn("GitHub enrichment failed: {}", e.getMessage());
            return EnrichmentResponse.error(e.getMessage());
        }
    }

    private EnrichmentResponse successFromGithub(String email, String login, String rawJson, EnrichmentRequest req) {
        String linkedinUrl = null;   // GitHub doesn't expose this
        // "Confidence" is a rough signal — a verified profile email is stronger than a commit scrape.
        int confidence = 60;
        // Emails observed in git commits or public profiles are real addresses
        // the person actively used. LIKELY — as strong as you get without SMTP.
        return EnrichmentResponse.success(
                email, null, null, linkedinUrl, confidence,
                EnrichmentResponse.Confidence.LIKELY,
                "{\"githubLogin\":\"" + login + "\",\"raw\":" + rawJson + "}");
    }

    private JsonNode get(String path, String token) throws Exception {
        var req = restClient.get().uri(path).header(HttpHeaders.ACCEPT, "application/vnd.github+json");
        if (token != null && !token.isBlank()) req = req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        String body = req.retrieve().body(String.class);
        return body == null ? null : mapper.readTree(body);
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field)) return null;
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    private static String safe(String s) { return s == null ? "" : s.trim(); }
}
