package com.leadrush.enrichment.adapter.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadrush.enrichment.adapter.EnrichmentProviderAdapter;
import com.leadrush.enrichment.util.RobotsTxtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Deeper-than-WebsiteScraper crawl:
//  1. Fetch /sitemap.xml (and any nested sitemaps).
//  2. Filter URLs likely to list people (team / about / staff / leadership / people / bio).
//  3. Fetch each candidate page respecting robots.txt + a small politeness delay.
//  4. Extract JSON-LD schema.org/Person entities (name + email + jobTitle) and mailto: links.
//  5. Score candidates against the contact's first/last name; return the best match.
//
// Works well on SMB / mid-market sites that publish team pages. Enterprise sites usually
// don't — the waterfall falls through to paid providers for those.
@Component
@RequiredArgsConstructor
@Slf4j
public class SitemapCrawlerAdapter implements EnrichmentProviderAdapter {

    private static final String KEY = "SITEMAP_CRAWLER";
    private static final int MAX_PAGES_TO_FETCH = 12;
    private static final int FETCH_TIMEOUT_MS = 5_000;
    private static final long POLITE_DELAY_MS = 500;

    private static final List<String> TEAM_HINTS = List.of(
            "team", "about", "staff", "leadership", "people", "bio", "management", "who-we-are", "our-team"
    );

    private static final Pattern EMAIL_RE = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern LOC_RE = Pattern.compile("<loc>\\s*(.*?)\\s*</loc>", Pattern.DOTALL);

    private final RobotsTxtService robots;
    private final RestClient http = RestClient.builder().build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override public String providerKey() { return KEY; }
    @Override public String displayName() { return "Sitemap crawler"; }
    @Override public boolean requiresApiKey() { return false; }

    @Override
    public EnrichmentResponse enrich(EnrichmentRequest request, String apiKey) {
        String domain = request.companyDomain();
        if (domain == null || domain.isBlank()) return EnrichmentResponse.notFound(null);
        if (isBlank(request.firstName()) && isBlank(request.lastName())) {
            return EnrichmentResponse.notFound(null);
        }

        try {
            List<DiscoveredPerson> discovered = crawlDomain(domain);
            if (discovered.isEmpty()) return EnrichmentResponse.notFound("no persons found on team pages");

            DiscoveredPerson best = pickBestMatch(discovered, request.firstName(), request.lastName());
            if (best == null) return EnrichmentResponse.notFound("no person-match on team pages");

            int confidence = best.fromJsonLd() ? 85 : 65;
            return EnrichmentResponse.success(
                    best.email(), null, best.title(), null, confidence,
                    "{\"source\":\"" + best.sourceUrl() + "\"}");
        } catch (Exception e) {
            log.warn("Sitemap crawler failed for {}: {}", domain, e.getMessage());
            return EnrichmentResponse.error(e.getMessage());
        }
    }

    // Publicly exposed so the background CompanyCrawlService can reuse the same crawl
    // logic without going through the adapter interface.
    public List<DiscoveredPerson> crawlDomain(String domain) {
        if (domain == null || domain.isBlank()) return List.of();

        List<String> teamPages = findTeamPages(domain);
        if (teamPages.isEmpty()) return List.of();

        List<DiscoveredPerson> discovered = new ArrayList<>();
        int fetched = 0;
        for (String url : teamPages) {
            if (fetched >= MAX_PAGES_TO_FETCH) break;
            if (!robots.isAllowed(url)) continue;
            try {
                Thread.sleep(POLITE_DELAY_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
            discovered.addAll(harvestPage(url, domain));
            fetched++;
        }
        return discovered;
    }

    // Public DTO — mirrors the private DiscoveredPerson record for external consumers.
    public record DiscoveredPerson(
            String name, String email, String title, String sourceUrl, boolean fromJsonLd
    ) {}

    // Walk the sitemap(s) and return the URL subset that likely contains people data.
    private List<String> findTeamPages(String domain) {
        String root = "https://" + domain + "/sitemap.xml";
        if (!robots.isAllowed(root)) return List.of();

        Set<String> all = new LinkedHashSet<>();
        List<String> queue = new ArrayList<>();
        queue.add(root);
        int processed = 0;

        while (!queue.isEmpty() && processed++ < 5) {   // cap nesting — sitemap-of-sitemaps
            String url = queue.remove(0);
            String xml = fetchText(url);
            if (xml == null) continue;

            Matcher m = LOC_RE.matcher(xml);
            while (m.find()) {
                String loc = m.group(1).trim();
                if (loc.endsWith(".xml")) queue.add(loc);
                else all.add(loc);
            }
        }

        return all.stream()
                .filter(this::looksLikeTeamPage)
                .distinct()
                .limit(MAX_PAGES_TO_FETCH)
                .toList();
    }

    private boolean looksLikeTeamPage(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return TEAM_HINTS.stream().anyMatch(h -> lower.contains("/" + h));
    }

    private List<DiscoveredPerson> harvestPage(String url, String domain) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(RobotsTxtService.UA_HEADER)
                    .timeout(FETCH_TIMEOUT_MS)
                    .ignoreHttpErrors(true)
                    .followRedirects(true)
                    .get();

            List<DiscoveredPerson> out = new ArrayList<>();

            // 1. JSON-LD structured data (highest-quality source)
            for (Element script : doc.select("script[type=application/ld+json]")) {
                try {
                    JsonNode root = mapper.readTree(script.data());
                    collectPersons(root, url, out);
                } catch (Exception ignore) { /* malformed JSON-LD — skip */ }
            }

            // 2. mailto: links near named elements (fallback)
            for (Element a : doc.select("a[href^=mailto]")) {
                String addr = a.attr("href").replaceFirst("(?i)^mailto:", "").split("[?#]")[0].trim();
                if (!addr.toLowerCase(Locale.ROOT).endsWith("@" + domain.toLowerCase(Locale.ROOT))) continue;
                // Try to find a person name nearby — link text or closest heading
                String name = a.text();
                if (name.isBlank() || name.contains("@")) name = nearestHeadingText(a);
                out.add(new DiscoveredPerson(name, addr.toLowerCase(Locale.ROOT), null, url, false));
            }

            // 3. Raw regex sweep of the page text for same-domain emails
            Matcher em = EMAIL_RE.matcher(doc.text());
            while (em.find()) {
                String addr = em.group().toLowerCase(Locale.ROOT);
                if (addr.endsWith("@" + domain.toLowerCase(Locale.ROOT))) {
                    out.add(new DiscoveredPerson(null, addr, null, url, false));
                }
            }
            return out;
        } catch (Exception e) {
            log.debug("Failed to harvest {}: {}", url, e.getMessage());
            return List.of();
        }
    }

    // Recursively walk a JSON-LD tree pulling out every node with @type = Person.
    // Many sites wrap persons in @graph or inside Organization.employee arrays.
    private void collectPersons(JsonNode node, String sourceUrl, List<DiscoveredPerson> out) {
        if (node == null || node.isNull()) return;
        if (node.isArray()) {
            for (JsonNode child : node) collectPersons(child, sourceUrl, out);
            return;
        }
        if (!node.isObject()) return;

        JsonNode typeNode = node.get("@type");
        if (typeNode != null) {
            String type = typeNode.asText("");
            if ("Person".equalsIgnoreCase(type)) {
                String name = text(node, "name");
                String email = text(node, "email");
                if (email != null) email = email.replaceFirst("(?i)^mailto:", "");
                String title = text(node, "jobTitle");
                if (title == null) title = text(node, "title");
                if (name != null || email != null) {
                    out.add(new DiscoveredPerson(name, email, title, sourceUrl, true));
                }
            }
        }

        // Recurse into known nested containers
        for (String field : List.of("@graph", "employee", "employees", "member", "members", "hasPart", "itemListElement")) {
            if (node.has(field)) collectPersons(node.get(field), sourceUrl, out);
        }
    }

    private String nearestHeadingText(Element link) {
        Element parent = link;
        for (int i = 0; i < 4 && parent != null; i++) {
            Element h = parent.selectFirst("h1, h2, h3, h4, h5, h6");
            if (h != null) return h.text();
            parent = parent.parent();
        }
        return link.text();
    }

    private DiscoveredPerson pickBestMatch(List<DiscoveredPerson> candidates, String first, String last) {
        String needleFirst = normaliseName(first);
        String needleLast = normaliseName(last);

        DiscoveredPerson exact = null, partial = null, anyWithEmail = null;
        for (DiscoveredPerson c : candidates) {
            if (c.email() == null) continue;
            if (anyWithEmail == null) anyWithEmail = c;

            String cn = normaliseName(c.name());
            if (cn.isEmpty()) continue;

            boolean hasFirst = !needleFirst.isEmpty() && cn.contains(needleFirst);
            boolean hasLast = !needleLast.isEmpty() && cn.contains(needleLast);

            if (hasFirst && hasLast && exact == null) exact = c;
            else if ((hasFirst || hasLast) && partial == null) partial = c;
        }
        if (exact != null) return exact;
        if (partial != null) return partial;
        return anyWithEmail;
    }

    private String fetchText(String url) {
        try {
            return http.get()
                    .uri(url)
                    .header("User-Agent", RobotsTxtService.UA_HEADER)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field)) return null;
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String normaliseName(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
}
