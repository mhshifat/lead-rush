package com.leadrush.enrichment.entity;

// Common email-format patterns, in roughly descending order of how often corporations use them.
// The first candidates generated are the most likely hits, so the pattern guesser burns the fewest
// SMTP probes before landing a valid address.
public enum EmailPatternType {
    FIRST_DOT_LAST,        // john.smith@acme.com
    FIRST_LAST,            // johnsmith@acme.com
    F_LAST,                // jsmith@acme.com
    F_DOT_LAST,            // j.smith@acme.com
    FIRST_L,               // johns@acme.com
    FIRST,                 // john@acme.com
    LAST,                  // smith@acme.com
    FIRST_UNDERSCORE_LAST, // john_smith@acme.com
    FIRST_DASH_LAST,       // john-smith@acme.com
    LAST_DOT_FIRST,        // smith.john@acme.com
    LAST_FIRST,            // smithjohn@acme.com
    LAST_F;                // smithj@acme.com

    public String apply(String first, String last, String domain) {
        String f = safe(first);
        String l = safe(last);
        String fi = initial(first);
        String li = initial(last);

        String local = switch (this) {
            case FIRST_DOT_LAST        -> f + "." + l;
            case FIRST_LAST            -> f + l;
            case F_LAST                -> fi + l;
            case F_DOT_LAST            -> fi + "." + l;
            case FIRST_L               -> f + li;
            case FIRST                 -> f;
            case LAST                  -> l;
            case FIRST_UNDERSCORE_LAST -> f + "_" + l;
            case FIRST_DASH_LAST       -> f + "-" + l;
            case LAST_DOT_FIRST        -> l + "." + f;
            case LAST_FIRST            -> l + f;
            case LAST_F                -> l + fi;
        };
        return local + "@" + domain;
    }

    private static String safe(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase().replaceAll("[^a-z0-9-]", "");
    }

    private static String initial(String s) {
        String safe = safe(s);
        return safe.isEmpty() ? "" : safe.substring(0, 1);
    }
}
