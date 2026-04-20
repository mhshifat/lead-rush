package com.leadrush.extension.entity;

import com.leadrush.common.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A LinkedIn search URL the user wants to revisit. Every time they land on
 * the same URL with the extension open, we compare the currently-visible
 * profile list against {@link #knownProfileUrls} to surface "new since last
 * save" — the scraping itself stays client-side (we have no LinkedIn session
 * server-side to replay their query).
 *
 * Stored as TEXT[] in Postgres for cheap set-diff, not a child table: each
 * search's known list rarely exceeds a few hundred URLs, and we never need
 * to query across them.
 */
@Entity
@Table(name = "saved_searches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedSearch extends TenantEntity {

    /** User-facing label, e.g. "SaaS founders, NYC". */
    @Column(nullable = false, length = 200)
    private String name;

    /** Canonical LinkedIn URL — matched verbatim on subsequent checks. */
    @Column(nullable = false, length = 2000)
    private String url;

    /**
     * Every profile URL (canonicalised via extension's normaliseProfileUrl) the
     * user has seen during previous visits to this search. Stored as a single
     * newline-separated TEXT blob — simpler than pg arrays, avoids a join, and
     * the lists stay small (a few hundred URLs per search). Service layer
     * splits/joins; repository consumers never touch the raw string.
     */
    @Column(name = "known_profile_urls", columnDefinition = "text")
    private String knownProfileUrlsRaw;

    @Column(name = "last_checked_at")
    private LocalDateTime lastCheckedAt;

    /** User who created the saved search — nice-to-have for "Bob's searches" filtering later. */
    @Column(name = "created_by_user_id")
    private UUID createdByUserId;
}
