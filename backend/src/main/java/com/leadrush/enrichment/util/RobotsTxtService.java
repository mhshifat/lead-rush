package com.leadrush.enrichment.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Minimalist robots.txt fetcher + evaluator. Not a full RFC 9309 implementation —
// just the common case: find the block for our user-agent (or * fallback), collect
// Disallow / Allow prefix rules, and answer isAllowed(path).
//
// Results cache per host for 1 hour. A missing/404 robots.txt means "allow everything"
// per convention; a 5xx means "be conservative — deny everything for a short while".
@Service
@Slf4j
public class RobotsTxtService {

    public static final String USER_AGENT = "LeadRushEnricher";
    public static final String UA_HEADER =
            "Mozilla/5.0 (compatible; LeadRushEnricher/1.0; +https://leadrush.local/bot)";

    private static final Duration TTL = Duration.ofHours(1);
    private static final Duration NEGATIVE_TTL = Duration.ofMinutes(5);

    private record CachedRules(Rules rules, Instant expiresAt) {
        boolean isFresh() { return Instant.now().isBefore(expiresAt); }
    }

    private record Rules(List<String> allow, List<String> disallow, int crawlDelaySeconds) {
        static Rules allowAll() { return new Rules(List.of(), List.of(), 0); }
        static Rules denyAll() { return new Rules(List.of(), List.of("/"), 0); }

        boolean isAllowed(String path) {
            // Longest-match wins (RFC 9309 §2.2.2). Find the most specific Allow / Disallow.
            String bestAllow = longestPrefix(path, allow);
            String bestDisallow = longestPrefix(path, disallow);
            if (bestDisallow == null) return true;
            if (bestAllow == null) return false;
            return bestAllow.length() >= bestDisallow.length();
        }

        private static String longestPrefix(String path, List<String> prefixes) {
            String best = null;
            for (String p : prefixes) {
                if (path.startsWith(p) && (best == null || p.length() > best.length())) best = p;
            }
            return best;
        }
    }

    private final Map<String, CachedRules> cache = new ConcurrentHashMap<>();
    private final RestClient http = RestClient.builder().build();

    public boolean isAllowed(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            String host = uri.getHost();
            String path = uri.getRawPath();
            if (host == null) return false;
            if (path == null || path.isEmpty()) path = "/";
            return rulesFor(host).isAllowed(path);
        } catch (Exception e) {
            return false;
        }
    }

    public int crawlDelaySeconds(String host) {
        return rulesFor(host).crawlDelaySeconds();
    }

    private Rules rulesFor(String host) {
        CachedRules cached = cache.get(host);
        if (cached != null && cached.isFresh()) return cached.rules();

        Rules fresh;
        try {
            String body = http.get()
                    .uri("https://" + host + "/robots.txt")
                    .header("User-Agent", UA_HEADER)
                    .retrieve()
                    .body(String.class);
            fresh = body == null || body.isBlank() ? Rules.allowAll() : parse(body);
            cache.put(host, new CachedRules(fresh, Instant.now().plus(TTL)));
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            fresh = Rules.allowAll();
            cache.put(host, new CachedRules(fresh, Instant.now().plus(TTL)));
        } catch (Exception e) {
            // Transient or blocked — deny-by-default for a short period rather than keep hammering.
            log.debug("robots.txt fetch failed for {}: {}", host, e.getMessage());
            fresh = Rules.denyAll();
            cache.put(host, new CachedRules(fresh, Instant.now().plus(NEGATIVE_TTL)));
        }
        return fresh;
    }

    // Parse the first block whose User-agent matches our UA name or is '*'.
    // If both are present, our exact-match block wins per spec.
    private Rules parse(String body) {
        Rules starRules = null;
        Rules meRules = null;

        List<String> currentAgents = new ArrayList<>();
        List<String> allow = new ArrayList<>();
        List<String> disallow = new ArrayList<>();
        int crawlDelay = 0;
        boolean inBlock = false;

        for (String rawLine : body.split("\\r?\\n")) {
            String line = rawLine.split("#", 2)[0].trim();
            if (line.isEmpty()) continue;

            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();

            if ("user-agent".equals(key)) {
                // New block starting — flush previous
                if (inBlock) {
                    Rules r = new Rules(List.copyOf(allow), List.copyOf(disallow), crawlDelay);
                    for (String agent : currentAgents) {
                        if ("*".equals(agent) && starRules == null) starRules = r;
                        if (agent.equalsIgnoreCase(USER_AGENT) && meRules == null) meRules = r;
                    }
                    currentAgents = new ArrayList<>();
                    allow = new ArrayList<>();
                    disallow = new ArrayList<>();
                    crawlDelay = 0;
                    inBlock = false;
                }
                currentAgents.add(value);
            } else if ("allow".equals(key)) { allow.add(value); inBlock = true; }
            else if ("disallow".equals(key)) { if (!value.isEmpty()) disallow.add(value); inBlock = true; }
            else if ("crawl-delay".equals(key)) {
                try { crawlDelay = Integer.parseInt(value); } catch (NumberFormatException ignore) {}
                inBlock = true;
            }
        }
        // Flush the final block
        if (inBlock) {
            Rules r = new Rules(List.copyOf(allow), List.copyOf(disallow), crawlDelay);
            for (String agent : currentAgents) {
                if ("*".equals(agent) && starRules == null) starRules = r;
                if (agent.equalsIgnoreCase(USER_AGENT) && meRules == null) meRules = r;
            }
        }

        if (meRules != null) return meRules;
        if (starRules != null) return starRules;
        return Rules.allowAll();
    }
}
