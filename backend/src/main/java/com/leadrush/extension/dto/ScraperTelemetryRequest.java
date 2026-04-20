package com.leadrush.extension.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * Telemetry ping from the extension when scraper selectors miss.
 * Lets us spot LinkedIn DOM changes quickly — when a particular field
 * suddenly starts showing up in {@code missedFields} across many workspaces,
 * the selector needs updating.
 *
 * No PII — we only report which FIELDS failed, not their values. The URL is
 * included so we can tell regular-profile misses from Sales Nav misses.
 */
@Data
public class ScraperTelemetryRequest {
    /** "profile" | "salesNav" — which layout the scraper was on. */
    @NotBlank
    private String layout;

    /** URL normalized by the scraper (no tracking params). */
    @NotBlank
    private String url;

    /** Fields the scraper expected to find but didn't. */
    private List<String> missedFields;

    /** Optional — scraper code version, for correlating with releases. */
    private String scraperVersion;
}
