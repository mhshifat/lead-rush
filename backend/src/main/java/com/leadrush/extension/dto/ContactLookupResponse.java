package com.leadrush.extension.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Summary of a contact the extension already knows about — rendered as the
 * "Already in Lead Rush" state in the LinkedIn side panel.
 *
 * Purpose: stop duplicate-looking imports, surface context (score, active
 * sequences, last touch) so the user knows what they're walking into before
 * they reach for Connect or Message on LinkedIn.
 */
@Value
@Builder
public class ContactLookupResponse {
    UUID contactId;
    String fullName;
    String title;
    String companyName;
    int leadScore;
    String lifecycleStage;
    String avatarUrl;

    /** Active sequences the contact is currently enrolled in. */
    List<String> activeSequenceNames;

    /** Timestamp of the most recent activity (email sent/opened/clicked, task, note, enrollment). */
    LocalDateTime lastActivityAt;

    LocalDateTime createdAt;

    /** Absolute URL to the contact's page in the admin app — deep-link target from the panel. */
    String contactUrl;

    /**
     * Recent teammate activity on this contact (last 14 days). Empty when
     * nobody else touched them — the panel hides the warning card entirely.
     */
    List<CollisionWarning> collisions;

    /**
     * Open deals involving this contact. Closed-won/lost are excluded — the
     * panel is about present context, not a historical ledger.
     */
    List<DealSummary> deals;

    /**
     * Job/company changes detected in the last 90 days from re-scrapes. Null
     * or empty when nothing recent. Lets the panel greet the rep with "Jane
     * just moved to TrendCo" on a revisit even when the re-scrape happened
     * on an earlier session.
     */
    List<JobChangeEvent> recentJobChanges;
}

