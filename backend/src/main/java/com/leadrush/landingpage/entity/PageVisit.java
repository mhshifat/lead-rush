package com.leadrush.landingpage.entity;

import com.leadrush.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Anonymous page visit log — for conversion analytics.
 * IP is stored as SHA-256 hash for GDPR (anonymous visitor counting, not identity tracking).
 *
 * Extends BaseEntity (not TenantEntity) because we still add workspace_id as a
 * regular column — visits are tied to a workspace but created via the PUBLIC page
 * endpoint where we don't have a tenant context from JWT.
 */
@Entity
@Table(name = "page_visits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageVisit extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "landing_page_id", nullable = false)
    private UUID landingPageId;

    @Column(name = "visitor_hash", length = 64)
    private String visitorHash;

    @Column(length = 500)
    private String referrer;

    @Column(name = "utm_source", length = 100)
    private String utmSource;

    @Column(name = "utm_medium", length = 100)
    private String utmMedium;

    @Column(name = "utm_campaign", length = 100)
    private String utmCampaign;

    @Column(name = "visited_at", nullable = false)
    @Builder.Default
    private LocalDateTime visitedAt = LocalDateTime.now();
}
