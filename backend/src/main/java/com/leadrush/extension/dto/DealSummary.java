package com.leadrush.extension.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Compact deal shape for the side panel. Excludes closed-won/lost deals by
 * default — the panel is about "what's open right now with this person".
 * Currency is kept at the deal level (not converted) — users tend to run
 * one currency per workspace.
 */
@Value
@Builder
public class DealSummary {
    UUID dealId;
    String name;
    String stageName;
    String stageColor;
    /** "OPEN" | "WON" | "LOST" — panel colours the chip accordingly. */
    String stageType;
    int winProbability;
    BigDecimal valueAmount;
    String valueCurrency;
    LocalDate expectedCloseAt;
    /** Deep-link into the web app. */
    String dealUrl;
}
