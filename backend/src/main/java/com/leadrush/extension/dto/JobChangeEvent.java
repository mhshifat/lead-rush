package com.leadrush.extension.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * A detected change to a contact's role or employer, captured whenever the
 * extension re-scrapes a known profile and the scraped values differ from
 * the stored ones. Powers "job change detected" banners in the side panel
 * so reps can reach out with a timely congratulations note instead of
 * chasing stale contact info.
 *
 * No paid enrichment API needed — the user's own LinkedIn browsing is the
 * polling mechanism.
 */
@Value
@Builder
public class JobChangeEvent {
    /** "TITLE" | "COMPANY" — lets the panel choose copy + icon. */
    String type;
    String from;
    String to;
    LocalDateTime at;
}
