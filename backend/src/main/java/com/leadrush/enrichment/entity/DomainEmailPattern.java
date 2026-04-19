package com.leadrush.enrichment.entity;

import com.leadrush.common.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// Workspace-scoped cache of email-format patterns discovered per domain.
// Powers the free, instant tier of the enrichment waterfall: once we've seen
// acme.com uses FIRST_DOT_LAST, every future contact at that domain skips external calls.
@Entity
@Table(name = "domain_email_patterns",
       uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "domain"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DomainEmailPattern extends TenantEntity {

    @Column(nullable = false)
    private String domain;

    @Enumerated(EnumType.STRING)
    @Column(name = "pattern_type", nullable = false)
    private EmailPatternType patternType;

    // Bumps each time we re-confirm the same pattern at this domain. Higher = more trustworthy.
    @Column(nullable = false)
    @Builder.Default
    private int confidence = 1;

    // True if the domain accepts any @domain recipient — pattern-guessing is unreliable here.
    @Column(name = "catch_all", nullable = false)
    @Builder.Default
    private boolean catchAll = false;

    // Which adapter discovered this pattern (HUNTER, SMTP_VERIFY, WEBSITE_SCRAPER, etc.)
    @Column(length = 40)
    private String source;

    @Column(name = "last_confirmed_at", nullable = false)
    @Builder.Default
    private LocalDateTime lastConfirmedAt = LocalDateTime.now();
}
