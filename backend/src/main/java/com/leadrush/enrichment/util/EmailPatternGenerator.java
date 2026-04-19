package com.leadrush.enrichment.util;

import com.leadrush.enrichment.entity.EmailPatternType;

import java.util.LinkedHashMap;
import java.util.Map;

// Generates candidate emails for a person at a domain using common corporate formats.
// Used by the pattern-guesser adapter (to probe via SMTP) and the domain-pattern adapter
// (to construct an email from an already-discovered pattern).
public final class EmailPatternGenerator {

    private EmailPatternGenerator() {}

    // Null or blank first/last yields an empty map — we can't build anything useful.
    public static Map<EmailPatternType, String> generate(String firstName, String lastName, String domain) {
        Map<EmailPatternType, String> out = new LinkedHashMap<>();
        if (isBlank(domain)) return out;

        boolean hasFirst = !isBlank(firstName);
        boolean hasLast = !isBlank(lastName);

        for (EmailPatternType type : EmailPatternType.values()) {
            // Skip patterns we can't satisfy given the inputs available.
            if (!hasFirst && requiresFirst(type)) continue;
            if (!hasLast && requiresLast(type)) continue;

            String email = type.apply(firstName, lastName, domain);
            // Guard against malformed output (e.g. ".smith@acme.com" when first is missing)
            String local = email.substring(0, email.indexOf('@'));
            if (local.isEmpty() || local.startsWith(".") || local.endsWith(".") || local.contains("..")) continue;

            out.put(type, email);
        }
        return out;
    }

    public static String applyPattern(EmailPatternType type, String firstName, String lastName, String domain) {
        if (type == null || isBlank(domain)) return null;
        if (requiresFirst(type) && isBlank(firstName)) return null;
        if (requiresLast(type) && isBlank(lastName)) return null;
        return type.apply(firstName, lastName, domain);
    }

    private static boolean requiresFirst(EmailPatternType t) {
        return switch (t) {
            case LAST -> false;
            default -> true;
        };
    }

    private static boolean requiresLast(EmailPatternType t) {
        return switch (t) {
            case FIRST -> false;
            default -> true;
        };
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
}
