package com.leadrush.email.tracking;

import com.leadrush.config.LeadRushProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Injects open pixel + rewrites http(s) anchors through the click endpoint + appends unsubscribe. */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrackingService {

    private static final String HMAC_ALGO = "HmacSHA256";

    // Only http/https anchors are rewritten (not mailto:, tel:, etc.).
    private static final Pattern LINK_PATTERN = Pattern.compile(
            "<a\\s+([^>]*?)href=\"(https?://[^\"]+)\"([^>]*)>",
            Pattern.CASE_INSENSITIVE
    );

    private final LeadRushProperties properties;
    private Mac mac;

    @PostConstruct
    public void init() throws Exception {
        mac = Mac.getInstance(HMAC_ALGO);
        mac.init(new SecretKeySpec(
                properties.getEncryptionKey().getBytes(StandardCharsets.UTF_8),
                HMAC_ALGO
        ));
    }

    public String wrapBody(String html, UUID executionId, UUID contactId) {
        if (html == null) return null;

        String result = html;

        result = rewriteLinks(result, executionId);

        String pixelUrl = buildTrackingUrl("/t/o/" + executionId);
        String pixel = "\n<img src=\"" + pixelUrl + "\" width=\"1\" height=\"1\" alt=\"\" style=\"display:none\" />";

        String unsubUrl = buildUnsubscribeUrl(contactId);
        String footer = "\n<div style=\"font-size:12px; color:#888; margin-top:20px; padding-top:12px; border-top:1px solid #eee;\">"
                + "If you no longer want to receive these emails, <a href=\"" + unsubUrl + "\">unsubscribe here</a>."
                + "</div>";

        String closingTag = "</body>";
        int idx = result.toLowerCase().lastIndexOf(closingTag);
        if (idx >= 0) {
            result = result.substring(0, idx) + footer + pixel + result.substring(idx);
        } else {
            result = result + footer + pixel;
        }

        return result;
    }

    private String rewriteLinks(String html, UUID executionId) {
        Matcher matcher = LINK_PATTERN.matcher(html);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String beforeHref = matcher.group(1);
            String originalUrl = matcher.group(2);
            String afterHref = matcher.group(3);

            // Skip already-tracked URLs to avoid double-wrapping.
            if (originalUrl.startsWith(properties.getFrontendUrl() + "/t/")
                    || originalUrl.startsWith(properties.getFrontendUrl() + "/unsub/")) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }

            String trackedUrl = buildTrackingUrl(
                    "/t/c/" + executionId + "?url=" + urlEncode(originalUrl)
            );
            String replacement = "<a " + beforeHref + "href=\"" + trackedUrl + "\"" + afterHref + ">";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public String generateUnsubscribeToken(UUID contactId) {
        byte[] hmac;
        synchronized (mac) { // Mac is not thread-safe
            hmac = mac.doFinal(contactId.toString().getBytes(StandardCharsets.UTF_8));
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac);
    }

    public boolean verifyUnsubscribeToken(UUID contactId, String token) {
        if (token == null) return false;
        String expected = generateUnsubscribeToken(contactId);
        // Constant-time comparison to prevent timing attacks.
        return constantTimeEquals(expected, token);
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private String buildTrackingUrl(String path) {
        return properties.getFrontendUrl() + path;
    }

    public String buildUnsubscribeUrl(UUID contactId) {
        return buildTrackingUrl("/unsub/" + contactId + "?token=" + generateUnsubscribeToken(contactId));
    }

    private String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
