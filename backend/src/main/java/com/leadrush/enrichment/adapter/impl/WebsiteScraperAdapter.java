package com.leadrush.enrichment.adapter.impl;

import com.leadrush.enrichment.adapter.EnrichmentProviderAdapter;
import com.leadrush.enrichment.entity.EmailPatternType;
import com.leadrush.enrichment.util.EmailPatternGenerator;
import com.leadrush.enrichment.util.RobotsTxtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Crawls the company website's likely "contact" surfaces and pulls same-domain email
// addresses out of the HTML (mailto: links and plain-text matches).
//
// Bias toward same-domain results: we filter scraped emails to only those matching
// the company's own domain, so we don't pick up vendor or analytics addresses.
@Component
@RequiredArgsConstructor
@Slf4j
public class WebsiteScraperAdapter implements EnrichmentProviderAdapter {

    private static final String KEY = "WEBSITE_SCRAPER";

    private final RobotsTxtService robots;

    private static final List<String> CANDIDATE_PATHS = List.of(
            "/", "/contact", "/contact-us", "/contacts",
            "/about", "/about-us", "/team", "/our-team",
            "/imprint", "/legal", "/privacy"
    );

    private static final Pattern EMAIL_RE = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final int FETCH_TIMEOUT_MS = 5_000;

    @Override public String providerKey() { return KEY; }
    @Override public String displayName() { return "Website scraper"; }
    @Override public boolean requiresApiKey() { return false; }

    @Override
    public EnrichmentResponse enrich(EnrichmentRequest request, String apiKey) {
        String domain = request.companyDomain();
        if (domain == null || domain.isBlank()) return EnrichmentResponse.notFound(null);

        Set<String> found = scrapeDomain(domain);
        if (found.isEmpty()) return EnrichmentResponse.notFound(null);

        // Prefer an email that matches a common pattern for this contact's name over a
        // generic inbox like info@ or hello@.
        String best = pickBest(found, request.firstName(), request.lastName(), domain);

        // Confidence: lower for generic inboxes, higher when we matched the person's name.
        int confidence = isGenericLocalPart(best) ? 40 : 70;
        String rawJson = "{\"candidates\":" + found + "}";
        return EnrichmentResponse.success(best, null, null, null, confidence, rawJson);
    }

    private Set<String> scrapeDomain(String domain) {
        Set<String> emails = new LinkedHashSet<>();
        for (String path : CANDIDATE_PATHS) {
            for (String scheme : List.of("https", "http")) {
                try {
                    String url = scheme + "://" + domain + path;
                    if (!robots.isAllowed(url)) continue;
                    Document doc = Jsoup.connect(url)
                            .userAgent(RobotsTxtService.UA_HEADER)
                            .timeout(FETCH_TIMEOUT_MS)
                            .ignoreHttpErrors(true)
                            .followRedirects(true)
                            .get();

                    // mailto: links are the highest-quality source
                    for (Element a : doc.select("a[href^=mailto]")) {
                        String href = a.attr("href");
                        String addr = href.replaceFirst("(?i)^mailto:", "").split("[?#]")[0].trim();
                        if (!addr.isEmpty() && addr.toLowerCase().endsWith("@" + domain.toLowerCase())) {
                            emails.add(addr.toLowerCase());
                        }
                    }

                    // Plain-text regex sweep of the rendered body
                    Matcher m = EMAIL_RE.matcher(doc.text());
                    while (m.find()) {
                        String addr = m.group().toLowerCase();
                        if (addr.endsWith("@" + domain.toLowerCase())) emails.add(addr);
                    }
                    break; // switch-scheme: succeeded, move on to next path
                } catch (Exception e) {
                    // swallow per-URL failures; many paths won't exist
                }
            }
        }
        return emails;
    }

    // Score candidates so a name-match wins over info@/hello@, etc.
    private String pickBest(Set<String> candidates, String firstName, String lastName, String domain) {
        Map<EmailPatternType, String> patternEmails = EmailPatternGenerator.generate(firstName, lastName, domain);
        for (String email : candidates) {
            if (patternEmails.values().stream().anyMatch(p -> p.equalsIgnoreCase(email))) return email;
        }
        // No name match — return the first non-generic one, else just the first.
        return candidates.stream()
                .filter(e -> !isGenericLocalPart(e))
                .findFirst()
                .orElseGet(() -> candidates.iterator().next());
    }

    private static final Set<String> GENERIC_LOCALS = Set.of(
            "info", "hello", "contact", "support", "sales", "admin",
            "help", "team", "office", "hi", "press", "media", "careers", "jobs");

    private boolean isGenericLocalPart(String email) {
        int at = email.indexOf('@');
        if (at <= 0) return true;
        return GENERIC_LOCALS.contains(email.substring(0, at).toLowerCase());
    }
}
